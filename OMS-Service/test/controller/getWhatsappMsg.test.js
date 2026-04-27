const getWhatsappMsg = require('../../src/utils/getWhatsappMsg');

describe('get whatsapp message', () => {
    test("get whatsapp message structured", () => {

        
        var res = getWhatsappMsg({
            template : "test_template",
            fromPhone : "+966115208332",
            phone : "+911234567890",
            is_arabic : 0,
            var_length : 2,
            media_url : "",
            dynamic_path:"",
            var_1 : "hello",
            var_2 : "hello_world",
            })
            console.log(JSON.stringify(res));
        });
  });