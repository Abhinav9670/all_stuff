const getWhatsappMsg = require('../../src/utils/getWhatsappMsg');

describe('services', () => {

    const expectation = {"requestBody":{"from":{"phone_number":"+966115208332"},"to":[{"phone_number":"+96123456789"}],"data":{"message_template":{"storage":"none","template_name":"sample_Template","language":{"policy":"deterministic","code":"en"},"template_data":[{"data":"undelivered_test1"},{"data":"undelivered_test2"},{"data":"undelivered_test3"},{"data":"undelivered_test4"}]}}},"error":false}
    it('getWhatsappMsg', async () => {
      const result = getWhatsappMsg({
        template : "sample_Template",
        fromPhone : "+966115208332",
        phone : "+96123456789",
        is_arabic : 0,
        var_length : 4,
        media_url : "",
        dynamic_path:"",
        var_1 : "undelivered_test1",
        var_2 : "undelivered_test2",
        var_3 : "undelivered_test3",
        var_4 : "undelivered_test4",
      });
      expect(JSON.stringify(result)).toBe(JSON.stringify(expectation));
    });
  });