/*
 * Copyright 2023 杭州白书科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.playedu.api.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.playedu.api.domain.Course;
import xyz.playedu.api.domain.CourseDepartment;
import xyz.playedu.api.domain.ResourceCourseCategory;
import xyz.playedu.api.exception.NotFoundException;
import xyz.playedu.api.mapper.CourseMapper;
import xyz.playedu.api.service.CourseDepartmentService;
import xyz.playedu.api.service.CourseService;
import xyz.playedu.api.service.internal.ResourceCourseCategoryService;
import xyz.playedu.api.types.paginate.CoursePaginateFiler;
import xyz.playedu.api.types.paginate.PaginationResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author tengteng
 * @description 针对表【courses】的数据库操作Service实现
 * @createDate 2023-02-24 14:14:01
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    @Autowired private CourseDepartmentService courseDepartmentService;

    @Autowired private ResourceCourseCategoryService courseCategoryService;

    @Override
    public PaginationResult<Course> paginate(int page, int size, CoursePaginateFiler filter) {
        filter.setPageStart((page - 1) * size);
        filter.setPageSize(size);

        PaginationResult<Course> pageResult = new PaginationResult<>();
        pageResult.setData(getBaseMapper().paginate(filter));
        pageResult.setTotal(getBaseMapper().paginateCount(filter));

        return pageResult;
    }

    @Override
    @Transactional
    public Course createWithCategoryIdsAndDepIds(
            String title,
            String thumb,
            String shortDesc,
            Integer isRequired,
            Integer isShow,
            Integer[] categoryIds,
            Integer[] depIds) {
        // 创建课程
        Course course = new Course();
        course.setTitle(title);
        course.setThumb(thumb);
        course.setShortDesc(shortDesc);
        course.setIsShow(isShow);
        course.setIsRequired(isRequired);
        course.setCreatedAt(new Date());
        course.setUpdatedAt(new Date());
        save(course);
        // 关联分类
        relateCategories(course, categoryIds);
        // 关联部门
        relateDepartments(course, depIds);

        return course;
    }

    @Override
    public void relateDepartments(Course course, Integer[] depIds) {
        if (depIds == null || depIds.length == 0) {
            return;
        }
        List<CourseDepartment> courseDepartments = new ArrayList<>();
        for (int i = 0; i < depIds.length; i++) {
            Integer tmpDepId = depIds[i];
            courseDepartments.add(
                    new CourseDepartment() {
                        {
                            setCourseId(course.getId());
                            setDepId(tmpDepId);
                        }
                    });
        }
        courseDepartmentService.saveBatch(courseDepartments);
    }

    @Override
    public void resetRelateDepartments(Course course, Integer[] depIds) {
        courseDepartmentService.removeByCourseId(course.getId());
        relateDepartments(course, depIds);
    }

    @Override
    public void relateCategories(Course course, Integer[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return;
        }
        List<ResourceCourseCategory> resourceCourseCategories = new ArrayList<>();
        for (int i = 0; i < categoryIds.length; i++) {
            Integer tmpCategoryId = categoryIds[i];
            resourceCourseCategories.add(
                    new ResourceCourseCategory() {
                        {
                            setCategoryId(tmpCategoryId);
                            setCourseId(course.getId());
                        }
                    });
        }
        courseCategoryService.saveBatch(resourceCourseCategories);
    }

    @Override
    public void resetRelateCategories(Course course, Integer[] categoryIds) {
        courseCategoryService.removeByCourseId(course.getId());
        relateCategories(course, categoryIds);
    }

    @Override
    @Transactional
    public void updateWithCategoryIdsAndDepIds(
            Course course,
            String title,
            String thumb,
            String shortDesc,
            Integer isRequired,
            Integer isShow,
            Integer[] categoryIds,
            Integer[] depIds) {
        Course newCourse = new Course();
        newCourse.setId(course.getId());
        newCourse.setTitle(title);
        newCourse.setThumb(thumb);
        newCourse.setIsShow(isShow);
        newCourse.setIsRequired(isRequired);
        newCourse.setShortDesc(shortDesc);

        updateById(newCourse);

        resetRelateCategories(newCourse, categoryIds);
        resetRelateDepartments(newCourse, depIds);
    }

    @Override
    public Course findOrFail(Integer id) throws NotFoundException {
        Course course = getOne(query().getWrapper().eq("id", id));
        if (course == null) {
            throw new NotFoundException("课程不存在");
        }
        return course;
    }

    @Override
    public List<Integer> getDepIdsByCourseId(Integer courseId) {
        return courseDepartmentService.getDepIdsByCourseId(courseId);
    }

    @Override
    public List<Integer> getCategoryIdsByCourseId(Integer courseId) {
        return courseCategoryService.getCategoryIdsByCourseId(courseId);
    }

    @Override
    public void updateClassHour(Integer courseId, Integer classHour) {
        Course course = new Course();
        course.setId(courseId);
        course.setClassHour(classHour);
        updateById(course);
    }

    @Override
    public void removeCategoryIdRelate(Integer categoryId) {
        courseCategoryService.removeByCategoryId(categoryId);
    }

    @Override
    public List<Course> chunks(List<Integer> ids, List<String> fields) {
        return list(query().getWrapper().in("id", ids).select(fields));
    }

    @Override
    public List<Course> chunks(List<Integer> ids) {
        if (ids == null || ids.size() == 0) {
            return new ArrayList<>();
        }
        return list(query().getWrapper().in("id", ids));
    }

    @Override
    public List<Course> getOpenCoursesAndShow(Integer limit) {
        return getBaseMapper().openCoursesAndShow(limit);
    }

    @Override
    public List<Course> getDepCoursesAndShow(List<Integer> depIds) {
        if (depIds == null || depIds.size() == 0) {
            return new ArrayList<>();
        }
        List<Integer> courseIds = courseDepartmentService.getCourseIdsByDepIds(depIds);
        if (courseIds == null || courseIds.size() == 0) {
            return new ArrayList<>();
        }
        return list(query().getWrapper().in("id", courseIds).eq("is_show", 1));
    }

    @Override
    public Map<Integer, List<Integer>> getCategoryIdsGroup(List<Integer> courseIds) {
        if (courseIds == null || courseIds.size() == 0) {
            return null;
        }
        Map<Integer, List<ResourceCourseCategory>> data =
                courseCategoryService
                        .list(courseCategoryService.query().getWrapper().in("course_id", courseIds))
                        .stream()
                        .collect(Collectors.groupingBy(ResourceCourseCategory::getCourseId));
        Map<Integer, List<Integer>> result = new HashMap<>();
        data.forEach(
                (courseId, records) -> {
                    result.put(
                            courseId,
                            records.stream().map(ResourceCourseCategory::getCategoryId).toList());
                });
        return result;
    }

    @Override
    public Map<Integer, List<Integer>> getDepIdsGroup(List<Integer> courseIds) {
        if (courseIds == null || courseIds.size() == 0) {
            return null;
        }
        Map<Integer, List<CourseDepartment>> data =
                courseDepartmentService
                        .list(
                                courseDepartmentService
                                        .query()
                                        .getWrapper()
                                        .in("course_id", courseIds))
                        .stream()
                        .collect(Collectors.groupingBy(CourseDepartment::getCourseId));
        Map<Integer, List<Integer>> result = new HashMap<>();
        data.forEach(
                (courseId, records) -> {
                    result.put(courseId, records.stream().map(CourseDepartment::getDepId).toList());
                });
        return result;
    }

    @Override
    public Long total() {
        return count();
    }
}
