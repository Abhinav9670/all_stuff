/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { getTemplateMap } = require('../../src/helpers/smsTemplates1');

console.log = jest.fn();
console.error = jest.fn();

const TEST_DATA = {
  BASE_INPUT: {
    firstname: 'John',
    lastname: 'Doe',
    registerCustomerName: 'John Doe',
    incrementId: 'ORD123',
    courierName: 'Test Courier',
    awbNumber: 'AWB123',
    estimatedDelivery: '2024-03-20',
    entityId: 'ENT123',
    pickupFailedDate: '2024-03-19',
    cpId: 'CP123',
    returnId: 'RET123',
    returnAwb: 'RAWB123',
    returnItemText: '2 T-shirts',
    creditMemoCurrency: 'AED',
    creditRefunded: 100,
    codPartialCancelAmount: 50,
    onlineRefundAmount: -1,
    order_currency_code: 'AED',
    returnedItemsResponse: {
      totalRmaCount: 2,
      qcFailedQty: 1,
      missingCount: 1
    },
    itemReturned: '1 T-shirt',
    returnData: {
      rmaData: {
        rma_inc_id: 'RMA123'
      }
    },
    returnUrl: 'https://example.com/cashgram',
    canceledItemCount: 2
  }
};

describe('sms_templates', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
    global.baseConfig = {
      cpIdMapping: {
        cp123: 'Mapped Courier',
      },
      configs: {
        trackingBaseUrl: 'https://track.test.com/'
      },
      smsConfig: {
        deeplinkUrlShipped: true
      }
    };
  });

  describe('template_map', () => {
    it('registration_success_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'registration_success'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{customer_name}}': 'John Doe'
      });
    });

    it('order_place_success_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'order_place_success'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{customer_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{delivery_date}}': '2024-03-20'
      });
    });

    it('shipped_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'shipped'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{courier_name}}': 'Test Courier',
        '{{delivery_date}}': '2024-03-20',
        '{{link}}': encodeURIComponent('https://stylishop.com/web?type=tracking&url=https://track.test.com/?waybill=AWB123')
      });
    });

    it('out_for_delivery_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'out_for_delivery'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{courier_name}}': 'Test Courier',
        '{{AWB}}': 'AWB123'
      });
    });

    it('pickup_failed_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'pickup_failed'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{courier_name}}': 'Test Courier',
        '{{delivery_date}}': '2024-03-19',
        '{{AWB_number}}': 'AWB123'
      });
    });

    it('delivered_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'delivered'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{order_entity_id}}': 'ENT123'
      });
    });

    it('return_create_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'return_create'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{return_id}}': 'RET123'
      });
    });

    it('return_awb_create_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'return_awb_create'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{return_id}}': 'RET123',
        '{{Return_AWB}}': 'RAWB123',
        '{{courier_name}}': 'Test Courier'
      });
    });

    it('dropoff_sms_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'dropoff_sms'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{return_id}}': 'RET123',
        '{{Return_AWB}}': 'RAWB123',
        '{{courier_name}}': 'Mapped Courier'
      });
    });

    it('refund_completed_cod_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'refund_completed_cod',
        onlineRefundAmount: -1,
        codPartialCancelAmount: 50
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{return_id}}': 'RET123',
        '{{currency_type}}': 'AED',
        '{{amount}}': 50
      });
    });

    it('refund_completed_online_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'refund_completed_online',
        onlineRefundAmount: -1,
        codPartialCancelAmount: 50
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{return_id}}': 'RET123',
        '{{currency_type}}': 'AED',
        '{{amount}}': 50
      });
    });

    it('short_pickup_refund_cod_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'short_pickup_refund_cod',
        onlineRefundAmount: -1,
        codPartialCancelAmount: 50
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{currency_type}}': 'AED',
        '{{items}}': '2 T-shirts',
        '{{amount}}': 50,
        '{{missing_items}}': undefined
      });
    });

    it('order_cod_partial_unfulfilment_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'order_cod_partial_unfulfilment'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{items}}': 2,
        '{{currency_type}}': 'AED',
        '{{order_id}}': 'ORD123',
        '{{amount}}': 50
      });
    });

    it('order_prepaid_fully_unfulfilment_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'order_prepaid_fully_unfulfilment'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{first_name}}': 'John',
        '{{currency_type}}': 'AED',
        '{{order_id}}': 'ORD123',
        '{{amount}}': 50
      });
    });

    it('handle_html_tags_in_dates_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'order_place_success',
        estimatedDelivery: '2024-03-20<sup>th</sup>'
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({
        '{{customer_name}}': 'John',
        '{{order_id}}': 'ORD123',
        '{{delivery_date}}': '2024-03-20th'
      });
    });

    it('handle_error_case_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'order_place_success',
        estimatedDelivery: null
      };

      const result = getTemplateMap(inputData);

      expect(result).toEqual({});
    });

    it('handle_unknown_sms_type_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'unknown_type',
        estimatedDelivery: '2024-03-20',
        pickupFailedDate: '2024-03-19'
      };

      const result = getTemplateMap(inputData);

      expect(result).toBeUndefined();
    });
  });
});