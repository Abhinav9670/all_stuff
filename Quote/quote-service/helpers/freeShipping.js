const { getStoreConfig, getAdminStoreConfig } = require('./utils');
const { freeShippingThresholdUpgraded } = require('./v6/freeShipping');

/**
 * Calculate shipping charges and threshold based on free shipping configuration
 * @param {Object} params - Parameters object
 * @param {Object} params.quote - Quote object
 * @param {string} params.storeId - Store ID
 * @param {string} params.xHeaderToken - Header token
 * @param {number} params.subtotalWithDiscount - Subtotal with discount
 * @returns {Promise<Object>} Promise resolving to object containing shippingCharges and shippingThreshold
 */
function calculateShippingCharges({ quote, storeId, xHeaderToken, subtotalWithDiscount }) {
    // Helper function to calculate shipping charges using old logic
    const calculateOldLogicShipping = () => {
        const shippingThreshold = Number(getStoreConfig(storeId, 'shipmentChargesThreshold') || 0);
        const freeShippingEnable = getAdminStoreConfig(storeId, 'freeShippingEnabled') || false;
        let shippingCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
        
        if (freeShippingEnable || subtotalWithDiscount >= shippingThreshold) {
            shippingCharges = 0;
        }
        
        return { shippingCharges, shippingThreshold };
    };

    return new Promise((resolve, reject) => {
        try {
            const enableNewFreeShippingConfig = getStoreConfig(storeId, 'enableNewFreeShippingConfig') || false;

            // If new config is not enabled, use old logic
            if (enableNewFreeShippingConfig) {
                return resolve(calculateOldLogicShipping());
            }

            // Try to get new free shipping configuration
            // Handle the async operation properly without making the executor async
            freeShippingThresholdUpgraded({
                quote,
                storeId,
                xHeaderToken,
                subtotal: subtotalWithDiscount
            })
            .then(result => {
                // Handle the new configuration result
                if (result) {
                    let shippingCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
                    
                    if (result.allowFreeshipping) {
                        shippingCharges = 0;
                    }
                    
                    resolve({
                        shippingCharges,
                        shippingThreshold: result.matchedSingleThreshold || 0
                    });
                } else {
                    // Fallback to old logic if no result
                    resolve(calculateOldLogicShipping());
                }
            })
            .catch(freeShippingError => {
                // console.error('Error calling freeShippingThresholdUpgraded:', freeShippingError);
                // Fallback to old logic if there's an error
                resolve(calculateOldLogicShipping());
            });
            
        } catch (error) {
            // Fallback to default behavior if there's any other error
            // console.error('Error in calculateShippingCharges:', error);
            resolve(calculateOldLogicShipping());
        }
    });
}

// Ensure the function is properly exported
module.exports = { calculateShippingCharges };
