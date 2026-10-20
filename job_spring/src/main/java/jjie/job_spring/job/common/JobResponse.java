package jjie.job_spring.job.common;

import java.time.LocalDate;

public record JobResponse(Long id, String companyName, String jobTitle, String city,
                          String sourcePlatform, String originalUrl, String salary,
                          String responsibilities, String requirements, String educationRequirement,
                          LocalDate applicationDeadline, String status) {
    public static JobResponse from(JobApplication job) {
        return new JobResponse(job.getId(), job.getCompanyName(), job.getJobTitle(), job.getCity(),
                job.getSourcePlatform(), job.getOriginalUrl(), job.getSalary(), job.getResponsibilities(),
                job.getRequirements(), job.getEducationRequirement(), job.getApplicationDeadline(), job.getStatus());
    }
}
