package org.styli.services.order.pojo.response.Order;

public class DummyReturnShipmentResponse {
    
    private boolean status;
    private String statusCode;
    private String statusMsg;
    private String csvFileName;
    private String csvFileContent;
    
    public boolean isStatus() {
        return status;
    }
    
    public void setStatus(boolean status) {
        this.status = status;
    }
    
    public String getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getStatusMsg() {
        return statusMsg;
    }
    
    public void setStatusMsg(String statusMsg) {
        this.statusMsg = statusMsg;
    }
    
    public String getCsvFileName() {
        return csvFileName;
    }
    
    public void setCsvFileName(String csvFileName) {
        this.csvFileName = csvFileName;
    }
    
    public String getCsvFileContent() {
        return csvFileContent;
    }
    
    public void setCsvFileContent(String csvFileContent) {
        this.csvFileContent = csvFileContent;
    }
}
