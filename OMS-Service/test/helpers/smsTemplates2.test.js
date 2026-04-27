/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { autoRefundTemplate } = require('../../src/helpers/smsTemplates2');

console.log = jest.fn();
console.error = jest.fn();

const TEST_DATA = {
  BASE_INPUT: {
    firstname: 'John',
    lastname: 'Doe',
    incrementId: 'ORD123',
    onlineRefundAmount: '100.00',
    returnData: {
      rmaData: {
        rma_inc_id: 'RMA123'
      },
      creditMemo: {
        order_currency_code: 'AED'
      },
      autoRefundResponse: {
        requestedQty: 2,
        returnedQty: 2
      }
    }
  }
};

describe('sms_templates', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
  });

  describe('autorefund_template', () => {
    it('autorefund_pp_less_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'autorefund_pp_less',
        returnData: {
          ...TEST_DATA.BASE_INPUT.returnData,
          autoRefundResponse: {
            requestedQty: 3,
            returnedQty: 1
          }
        }
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#amount_currency}}': 'AED 100.00',
        '{{#items}}': 3,
        '{{#missing_items_count}}': 2
      });
    });

    it('autorefund_cod_less_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'autorefund_cod_less',
        returnData: {
          ...TEST_DATA.BASE_INPUT.returnData,
          autoRefundResponse: {
            requestedQty: 5,
            returnedQty: 3
          }
        }
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#amount_currency}}': 'AED 100.00',
        '{{#items}}': 5,
        '{{#missing_items_count}}': 2
      });
    });

    it('autorefund_pp_equal_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'autorefund_pp_equal'
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#order_id}}': 'ORD123',
        '{{#return_id}}': 'RMA123',
        '{{#amount_currency}}': 'AED 100.00'
      });
    });

    it('autorefund_cod_equal_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'autorefund_cod_equal'
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#order_id}}': 'ORD123',
        '{{#return_id}}': 'RMA123',
        '{{#amount_currency}}': 'AED 100.00'
      });
    });

    it('autorefund_pp_excess_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'autorefund_pp_excess',
        returnData: {
          ...TEST_DATA.BASE_INPUT.returnData,
          autoRefundResponse: {
            requestedQty: 2,
            returnedQty: 5
          }
        }
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#items}}': 2,
        '{{#excess_items_count}}': 3,
        '{{#amount_currency}}': 'AED 100.00'
      });
    });

    it('autorefund_cod_excess_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'autorefund_cod_excess',
        returnData: {
          ...TEST_DATA.BASE_INPUT.returnData,
          autoRefundResponse: {
            requestedQty: 3,
            returnedQty: 7
          }
        }
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#items}}': 3,
        '{{#excess_items_count}}': 4,
        '{{#amount_currency}}': 'AED 100.00'
      });
    });

    it('autorefund_unknown_type_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        smsType: 'unknown_type'
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toBeUndefined();
    });

    it('autorefund_missing_return_data_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        returnData: undefined,
        smsType: 'autorefund_pp_less'
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#amount_currency}}': 'undefined 100.00',
        '{{#items}}': 0,
        '{{#missing_items_count}}': 0
      });
    });

    it('autorefund_missing_refund_response_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        returnData: {
          ...TEST_DATA.BASE_INPUT.returnData,
          autoRefundResponse: undefined
        },
        smsType: 'autorefund_pp_less'
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#amount_currency}}': 'AED 100.00',
        '{{#items}}': 0,
        '{{#missing_items_count}}': 0
      });
    });

    it('autorefund_missing_credit_memo_valid', () => {
      const inputData = {
        ...TEST_DATA.BASE_INPUT,
        returnData: {
          ...TEST_DATA.BASE_INPUT.returnData,
          creditMemo: undefined
        },
        smsType: 'autorefund_pp_equal'
      };

      const result = autoRefundTemplate(inputData);

      expect(result).toEqual({
        '{{CName}}': 'John Doe',
        '{{#order_id}}': 'ORD123',
        '{{#return_id}}': 'RMA123',
        '{{#amount_currency}}': 'undefined 100.00'
      });
    });
  });
});