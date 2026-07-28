package com.smartstudy.studyroom.dto;

public class SeatImportResult {

    private Integer successCount;
    private Integer failCount;

    public SeatImportResult() {
    }

    public SeatImportResult(Integer successCount, Integer failCount) {
        this.successCount = successCount;
        this.failCount = failCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }
}
