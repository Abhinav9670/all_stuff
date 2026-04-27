package org.styli.services.order.pojo.oms;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "Shipment")
@XmlAccessorType(XmlAccessType.FIELD)
public class DispatchUpdateRequest {

    @XmlAttribute(name = "Action")
    private String action;

    @XmlAttribute(name = "DocumentType")
    private String documentType;

    @XmlAttribute(name = "ActualShipmentDate")
    private String actualShipmentDate;

    @XmlAttribute(name = "EnterpriseCode")
    private String enterpriseCode;

    @XmlAttribute(name = "SellerOrganizationCode")
    private String sellerOrganizationCode;

    @XmlAttribute(name = "ShipNode")
    private String shipNode;

    @XmlAttribute(name = "ShipmentNo")
    private String shipmentNo;

    @XmlAttribute(name = "DestinationZone")
    private String destinationZone;

    @XmlAttribute(name = "ReceivingNode")
    private String receivingNode;

    @XmlAttribute(name = "FinalShipment")
    private String finalShipment;

    @XmlAttribute(name = "MultipleShipmentCase")
    private String multipleShipmentCase;

    @XmlElement(name = "ShipmentLines")
    private ShipmentLines shipmentLines;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getActualShipmentDate() {
        return actualShipmentDate;
    }

    public void setActualShipmentDate(String actualShipmentDate) {
        this.actualShipmentDate = actualShipmentDate;
    }

    public String getEnterpriseCode() {
        return enterpriseCode;
    }

    public void setEnterpriseCode(String enterpriseCode) {
        this.enterpriseCode = enterpriseCode;
    }

    public String getSellerOrganizationCode() {
        return sellerOrganizationCode;
    }

    public void setSellerOrganizationCode(String sellerOrganizationCode) {
        this.sellerOrganizationCode = sellerOrganizationCode;
    }

    public String getShipNode() {
        return shipNode;
    }

    public void setShipNode(String shipNode) {
        this.shipNode = shipNode;
    }

    public String getShipmentNo() {
        return shipmentNo;
    }

    public void setShipmentNo(String shipmentNo) {
        this.shipmentNo = shipmentNo;
    }

    public String getDestinationZone() {
        return destinationZone;
    }

    public void setDestinationZone(String destinationZone) {
        this.destinationZone = destinationZone;
    }

    public String getReceivingNode() {
        return receivingNode;
    }

    public void setReceivingNode(String receivingNode) {
        this.receivingNode = receivingNode;
    }

    public String getFinalShipment() {
        return finalShipment;
    }

    public void setFinalShipment(String finalShipment) {
        this.finalShipment = finalShipment;
    }

    public String getMultipleShipmentCase() {
        return multipleShipmentCase;
    }

    public void setMultipleShipmentCase(String multipleShipmentCase) {
        this.multipleShipmentCase = multipleShipmentCase;
    }

    public ShipmentLines getShipmentLines() {
        return shipmentLines;
    }

    public void setShipmentLines(ShipmentLines shipmentLines) {
        this.shipmentLines = shipmentLines;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ShipmentLines {
        @XmlElement(name = "ShipmentLine")
        private List<ShipmentLine> shipmentLine;

        public List<ShipmentLine> getShipmentLine() {
            return shipmentLine;
        }

        public void setShipmentLine(List<ShipmentLine> shipmentLine) {
            this.shipmentLine = shipmentLine;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ShipmentLine {
        @XmlAttribute(name = "ItemID")
        private String itemID;

        @XmlAttribute(name = "HoldReasonCode")
        private String holdReasonCode;

        @XmlAttribute(name = "ShortPickQty")
        private String shortPickQty;

        @XmlAttribute(name = "UnitOfMeasure")
        private String unitOfMeasure;

        @XmlAttribute(name = "OrderNo")
        private String orderNo;

        @XmlAttribute(name = "PrimeLineNo")
        private String primeLineNo;

        @XmlAttribute(name = "ReleaseNo")
        private String releaseNo;

        @XmlAttribute(name = "ShipmentLineNo")
        private String shipmentLineNo;

        @XmlAttribute(name = "IsKitItem")
        private String isKitItem;

        @XmlAttribute(name = "Quantity")
        private String quantity;

        @XmlAttribute(name = "SubLineNo")
        private String subLineNo;

        public String getItemID() {
            return itemID;
        }

        public void setItemID(String itemID) {
            this.itemID = itemID;
        }

        public String getHoldReasonCode() {
            return holdReasonCode;
        }

        public void setHoldReasonCode(String holdReasonCode) {
            this.holdReasonCode = holdReasonCode;
        }

        public String getShortPickQty() {
            return shortPickQty;
        }

        public void setShortPickQty(String shortPickQty) {
            this.shortPickQty = shortPickQty;
        }

        public String getUnitOfMeasure() {
            return unitOfMeasure;
        }

        public void setUnitOfMeasure(String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getPrimeLineNo() {
            return primeLineNo;
        }

        public void setPrimeLineNo(String primeLineNo) {
            this.primeLineNo = primeLineNo;
        }

        public String getReleaseNo() {
            return releaseNo;
        }

        public void setReleaseNo(String releaseNo) {
            this.releaseNo = releaseNo;
        }

        public String getShipmentLineNo() {
            return shipmentLineNo;
        }

        public void setShipmentLineNo(String shipmentLineNo) {
            this.shipmentLineNo = shipmentLineNo;
        }

        public String getIsKitItem() {
            return isKitItem;
        }

        public void setIsKitItem(String isKitItem) {
            this.isKitItem = isKitItem;
        }

        public String getQuantity() {
            return quantity;
        }

        public void setQuantity(String quantity) {
            this.quantity = quantity;
        }

        public String getSubLineNo() {
            return subLineNo;
        }

        public void setSubLineNo(String subLineNo) {
            this.subLineNo = subLineNo;
        }
    }
}

