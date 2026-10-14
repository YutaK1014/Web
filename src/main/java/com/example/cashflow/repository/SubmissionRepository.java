package com.example.cashflow.repository;

import org.apache.ibatis.annotations.*;

@Mapper
public interface SubmissionRepository {
    @Insert("INSERT INTO registration_submissions(submission_id, form_type) VALUES(#{id}, #{type})")
    void insert(@Param("id") String id, @Param("type") String type);
}
