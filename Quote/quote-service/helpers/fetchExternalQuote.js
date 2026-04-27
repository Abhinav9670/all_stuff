const { logError } = require('./utils');
const quoteColumns = ['entity_id', 'store_id', 'created_at', 'updated_at', 'converted_at', 'is_active', 'is_virtual', 'is_multi_shipping', 'items_count', 'items_qty', 'orig_order_id', 'store_to_base_rate', 'store_to_quote_rate', 'base_currency_code', 'store_currency_code', 'quote_currency_code', 'grand_total', 'base_grand_total', 'checkout_method', 'customer_id', 'customer_tax_class_id', 'customer_group_id', 'customer_email', 'customer_prefix', 'customer_firstname', 'customer_middlename', 'customer_lastname', 'customer_suffix', 'customer_dob', 'customer_note', 'customer_note_notify', 'customer_is_guest', 'remote_ip', 'applied_rule_ids', 'reserved_order_id', 'password_hash', 'coupon_code', 'global_currency_code', 'base_to_global_rate', 'base_to_quote_rate', 'customer_taxvat', 'customer_gender', 'subtotal', 'base_subtotal', 'subtotal_with_discount', 'base_subtotal_with_discount', 'is_changed', 'trigger_recollect', 'ext_shipping_info', 'gift_message_id', 'is_persistent', 'amstorecredit_use', 'amstorecredit_base_amount', 'amstorecredit_amount', 'source', 'promo_applied_at', 'coupon_source_external'];
const quoteItemColumns = ['item_id', 'quote_id', 'created_at', 'updated_at', 'product_id', 'store_id', 'parent_item_id', 'is_virtual', 'sku', 'name', 'description', 'applied_rule_ids', 'additional_data', 'is_qty_decimal', 'no_discount', 'weight', 'qty', 'price', 'base_price', 'custom_price', 'discount_percent', 'discount_amount', 'base_discount_amount', 'tax_percent', 'tax_amount', 'base_tax_amount', 'row_total', 'base_row_total', 'row_total_with_discount', 'row_weight', 'product_type', 'base_tax_before_discount', 'tax_before_discount', 'original_custom_price', 'redirect_url', 'base_cost', 'price_incl_tax', 'base_price_incl_tax', 'row_total_incl_tax', 'base_row_total_incl_tax', 'discount_tax_compensation_amount', 'base_discount_tax_compensation_amount', 'gift_message_id', 'free_shipping', 'weee_tax_applied', 'weee_tax_applied_amount', 'weee_tax_applied_row_amount', 'weee_tax_disposition', 'weee_tax_row_disposition', 'base_weee_tax_applied_amount', 'base_weee_tax_applied_row_amnt', 'base_weee_tax_disposition', 'base_weee_tax_row_disposition', 'sku_size_map'];
const selectQuoteCols = quoteColumns.reduce((query, col) => {
    return `${query} q.${col} as 'q_${col}',`;
}, '')

const selectQuoteItemCols = quoteItemColumns.reduce((query, col) => {
    return `${query} qi.${col} as 'qi_${col}',`;
}, '')

const lastUpdatedAfter = process.env.IMPORT_LAST_UPDATED_AFTER;

exports.fetchExternalQuote = async ({ customerId, quoteIdArray, pool, xHeaderToken }) => {
    let response = {};

    try {
        let filterCondition = "";
        let formattedData = {};
        if (customerId) {
            filterCondition = `and q.customer_id = ${customerId}`
        } else {
            const quoteIds = quoteIdArray.join(",");
            filterCondition = `and q.entity_id in (${quoteIds})`
        }

        const quoteJoinQuery = `SELECT c.sku,${selectQuoteCols} ${selectQuoteItemCols.slice(0, -1)}
            FROM quote q
            LEFT JOIN quote_item qi ON q.entity_id  = qi.quote_id 
            LEFT JOIN catalog_product_entity c ON qi.product_id = c.entity_id 
            where  q.updated_at > '${lastUpdatedAfter}' ${filterCondition}  order by qi.item_id `;

        const queryResponse = await pool.query(quoteJoinQuery);

        const formattingError = [];
        let [data] = queryResponse;

        data = JSON.parse(JSON.stringify(data));

        for (const itemIndex in data) {
            try {
                const quote = data[itemIndex];
                if (!formattedData[quote.q_entity_id]) {
                    formattedData[quote.q_entity_id] = { quoteData: {}, itemData: {} };
                }
                const quoteData = {
                    "id": String(quote.q_entity_id),
                    "storeId": quote.q_store_id,
                    "source": quote.q_source,
                    "customerId": String(quote.q_customer_id || '') || '',
                    "customerEmail": quote.q_customer_email || '',
                    "customerFirstname": quote.q_customer_firstname || '',
                    "customerLastname": quote.q_customer_lastname || '',
                    "customerDob": quote.q_customer_dob || '',
                    "customerIsGuest": quote.q_customer_id ? 0 : 1,
                    "convertedAt": quote.q_converted_at || '',
                    "isActive": quote.q_is_active,
                    "itemsCount": Number(quote.q_items_count) || 0,
                    "itemsQty": Number(quote.q_items_qty) || 0,
                    "storeToBaseRate": Number(quote.q_store_to_base_rate || 0),
                    "baseCurrencyCode": String(quote.q_base_currency_code) || '',
                    "storeCurrencyCode": String(quote.q_base_currency_code) || '',
                    "grandTotal": Number(quote.q_grand_total || 0),
                    "baseGrandTotal": Number(quote.q_base_grand_total || 0),
                    "appliedRuleIds": typeof quote.q_applied_rule_ids === 'string' && quote.q_applied_rule_ids ? [quote.q_applied_rule_ids] : quote.q_applied_rule_ids,
                    "couponCode": String(quote.q_coupon_code),
                    "subtotal": Number(quote.q_subtotal),
                    "baseSubtotal": Number(quote.q_base_subtotal),
                    "subtotalWithDiscount": Number(quote.q_subtotal_with_discount),
                    "baseSubtotalWithDiscount": Number(quote.q_base_subtotal_with_discount),
                    "triggerRecollect": Number(quote.q_trigger_recollect),
                    "amstorecreditUse": Boolean(quote.q_amstorecredit_use),
                    "amstorecreditBaseAmount": Number(quote.q_amstorecredit_base_amount),
                    "amstorecreditAmount": Number(quote.q_amstorecredit_amount),
                    "promoAppliedAt": quote.q_promo_applied_at || '',
                    "updatedAt": quote.q_updated_at || '',
                    "createdAt": quote.q_created_at || '',
                    "quotePayment": {
                        method: null,
                        additionalInformation: null,
                        createdAt: null,
                        updatedAt: null
                    }
                };

                formattedData[quote.q_entity_id].quoteData = { ...formattedData[quote.q_entity_id].quoteData, ...quoteData };
                if (quote.qi_product_type === "simple") {
                    const simpleData = {
                        "productId": String(quote.qi_product_id || ''),
                        "storeId": quote.qi_store_id,
                        "sku": quote.sku,
                        "discountAmount": 0,
                        "baseDiscountAmount": 0,
                        "taxPercent": 0,
                        "taxAmount": 0,
                        "baseTaxAmount": 0,
                        "rowTotal": 0,
                        "baseRowTotal": 0,
                        "rowTotalWithDiscount": 0,
                        "productType": "simple",
                        "priceInclTax": 0,
                        "basePriceInclTax": 0,
                        "rowTotalInclTax": 0,
                        "baseRowTotalInclTax": 0,
                        "discountTaxCompensationAmount": 0,
                        "baseDiscountTaxCompensationAmount": 0,
                        "superAttributeId": "",
                        "superAttributeValue": "",
                        "superAttributeLabel": ""
                    }
                    formattedData[quote.q_entity_id].itemData = { ...formattedData[quote.q_entity_id].itemData, ...simpleData };
                } else {
                    const configData = {
                        "parentProductId": String(quote.qi_product_id),
                        "parentSku": String(quote.sku),
                        "name": quote.qi_name || '',
                        "qty": Number(quote.qi_qty),
                        "price": Number(quote.qi_price) || 0,
                        "basePrice": Number(quote.qi_base_price) || 0,
                        "discountPercent": Number(quote.qi_discount_percent) || 0
                    }
                    formattedData[quote.q_entity_id].itemData = { ...formattedData[quote.q_entity_id].itemData, ...configData };
                }

                if (formattedData[quote.q_entity_id]?.itemData?.sku && formattedData[quote.q_entity_id]?.itemData?.parentSku) {
                    if (formattedData[quote.q_entity_id].quoteData.quoteItem) {
                        formattedData[quote.q_entity_id].quoteData.quoteItem.push(formattedData[quote.q_entity_id]?.itemData);
                    } else {
                        formattedData[quote.q_entity_id].quoteData.quoteItem = [formattedData[quote.q_entity_id]?.itemData];
                    }
                    formattedData[quote.q_entity_id].itemData = {};
                }
            } catch (e) {
                logError(e, 'Error fetching from mysql', xHeaderToken)
                formattingError.push(e.message);

            }
        }
        if (formattingError.length)
            formattedData = {};

        response = { formattingError, formattedData }

    } catch (e) {
        logError(e, 'error getting external quote', xHeaderToken);
        response = { ...response, fetchError: e.message }
    }
    return response;

}
