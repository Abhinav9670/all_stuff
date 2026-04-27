const { 
  updateSplitSellerOrderStatus,
  createSellerAsnRecord,
  extractSellerIdFromIncrementId,
  getSellerConfigBySellerId,
  isPickedUpByStyliEnabled,
  publishReceivedByStyliStatus
} = require('../helpers/sellerAsnHelper');
const { mapNewPayloadToExisting } = require('../utils/shipmentPayloadMapper');
const logger = require('../config/logger');
/**
 * Service for handling seller shipment updates
 */
class SellerShipmentService {
  /**
   * Check if the given reference number is a seller order
   * @param {string} referenceNumber - The reference number to check
   * @returns {boolean} - True if it's a seller order
   */
  isSellerOrder(referenceNumber) {
    if (typeof referenceNumber !== "string") return false;
    const parts = referenceNumber.split("-");
    return parts.length > 2; // must have at least 3 parts
  }

  /**
   * Process seller shipment update
   * @param {Object} shipmentData - The shipment update data
   * @returns {Promise<Object>} - Processing result
   */
  async processSellerShipmentUpdate(shipmentData) {
    try {
      console.log(`### seller shipment update data: ${JSON.stringify(shipmentData)}`);
      
      const processedBody = mapNewPayloadToExisting(shipmentData) ?? shipmentData;
      
      const { waybill, additional = {}, rtoAwb, status: reqStatus } = processedBody;
      const {
        latest_status = {},
        notification_event_id: notificationId,
        ndr_status_description,
        is_rvp: isReturn
      } = additional;

      const {
        reference_number: increment_id,
        timestamp
      } = latest_status;

      // Validate that this is a seller order
      if (!this.isSellerOrder(increment_id)) {
        return {
          success: false,
          status: false,
          statusMsg: 'This endpoint only processes seller orders',
          errorCode: 'INVALID_ORDER_TYPE'
        };
      }

      let response = {};
      
      if (this.isSellerOrder(increment_id)) {
        // Handle forward shipments for seller orders
        response = await updateSplitSellerOrderStatus({
          increment_id,
          timestamp,
          notificationId,
          waybill,
          ndr_status_description,
          rtoAwb,
          reqStatus
        });
      }

      // Create or update Seller ASN record for forward shipments (PICKED_UP/SHIPPED)
      try {
        if (!isReturn && notificationId && (notificationId === 2 || notificationId === 3) && this.isSellerOrder(increment_id)) {
          const asnResult = await createSellerAsnRecord(processedBody);
          if (asnResult.success) {
            logger.info(`[SellerShipment] ASN created successfully for ${increment_id}: ${asnResult.asnId}`);
          } else {
            logger.info(`[SellerShipment] ASN creation result for ${increment_id}: ${asnResult.message}`);
          }
        }
      } catch (asnError) {
        // Log error but don't fail the shipment update
        logger.error(`[SellerShipment] Error processing ASN for ${increment_id}:`, asnError.message);
      }

      // NEW LAYER: Send "received_by_styli" Pub/Sub for DELIVERED status (picked_up_by_styli sellers only)
      try {
        if (!isReturn && notificationId === 5 && this.isSellerOrder(increment_id)) {
          logger.info(`[ReceivedByStyli] DELIVERED status received for ${increment_id}, checking flags...`);
          
          const sellerCentralAsnEnhancement = global.baseConfig?.seller_central_asn_enhancement;
          logger.info(`[ReceivedByStyli] seller_central_asn_enhancement flag: ${sellerCentralAsnEnhancement}`);
          
          if (sellerCentralAsnEnhancement === true) {
            const sellerId = extractSellerIdFromIncrementId(increment_id);
            logger.info(`[ReceivedByStyli] Extracted sellerId: ${sellerId} from ${increment_id}`);
            
            const sellerConfig = await getSellerConfigBySellerId(sellerId, null);
            const pickedUpByStyli = isPickedUpByStyliEnabled(sellerConfig);
            logger.info(`[ReceivedByStyli] picked_up_by_styli flag for seller ${sellerId}: ${pickedUpByStyli}`);
            
            if (pickedUpByStyli) {
              logger.info(`[ReceivedByStyli] Both flags TRUE - sending Pub/Sub for ${increment_id}`);
              
              const pubsubResult = await publishReceivedByStyliStatus({
                increment_id,
                waybill,
                sellerId,
                timestamp,
                asnId: null,  // ASN ID can be added if needed
                orderCode: null  // Order code can be added if needed
              });
              
              if (pubsubResult.success) {
                logger.info(`[ReceivedByStyli] Pub/Sub sent successfully for ${increment_id}, messageId: ${pubsubResult.messageId}`);
              } else {
                logger.error(`[ReceivedByStyli] Pub/Sub failed for ${increment_id}: ${pubsubResult.error}`);
              }
            } else {
              logger.info(`[ReceivedByStyli] picked_up_by_styli is FALSE for seller ${sellerId}, skipping Pub/Sub`);
            }
          } else {
            logger.info(`[ReceivedByStyli] seller_central_asn_enhancement is FALSE, skipping Pub/Sub for ${increment_id}`);
          }
        }
      } catch (pubsubError) {
        // Log error but don't fail the main flow
        logger.error(`[ReceivedByStyli] Error sending Pub/Sub for ${increment_id}:`, pubsubError.message);
      }

      if (!response.status) {
        return {
          success: false,
          status: false,
          statusMsg: response.errorMsg || 'Failed to update seller shipment',
          errorCode: 'SHIPMENT_UPDATE_FAILED'
        };
      }

      return {
        success: true,
        status: response.status,
        statusMsg: 'Seller Shipment Update Successfull',
        data: {
          increment_id,
          waybill,
          notificationId,
          timestamp
        }
      };

    } catch (error) {
      console.error("Error in seller shipment service:", error?.message ? JSON.stringify(error.message) : '', error);
      return {
        success: false,
        status: false,
        statusMsg: 'Internal server error',
        errorCode: 'INTERNAL_ERROR',
        error: error.message
      };
    }
  }
}

module.exports = new SellerShipmentService();
