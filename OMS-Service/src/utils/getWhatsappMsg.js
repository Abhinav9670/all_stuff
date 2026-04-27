const getWpMessage = (body) => {
    const {
      template,
      fromPhone = "+966115208332",
      phone,
      is_arabic = 0,
      var_length = 1,
      media_url = "",
      dynamic_path="",
      ...rest
    } = body;
    const isArabic = Boolean(Number(is_arabic));
    const dataArray = [];
    let variablesProper = true;
    let requestBody = {};
  
    let customerPhone = phone;
    customerPhone = customerPhone.replace(/\s+/g, "");
    if (!customerPhone.startsWith("+")) customerPhone = "+" + customerPhone;
  
    for (let i = 1; i <= var_length; i++) {
      if (!body[`var_${i}`]) variablesProper = false;
      dataArray.push({
        data: String(body[`var_${i}`]),
      });
    }
    if (!variablesProper) return { requestBody: {}, error: true };
  
    requestBody = {
      from: {
        phone_number: fromPhone,
      },
      to: [
        {
          phone_number: customerPhone,
        },
      ],
      data: {
        message_template: {
          storage: media_url ? "conversation" : "none",
          namespace: global.baseConfig?.whatsappConfig?.namespace,
          template_name: template, // in small case
          language: {
            policy: "deterministic",
            code: `${isArabic ? "ar" : "en"}`, // check the lang code approved in the template
          },
          // template_data: dataArray,
        },
      },
    };
    if (media_url || dynamic_path) {
      let richObject = { 
        body: {
        params: dataArray,
        }
       };
      if (media_url){
        richObject.header = { type: "image", media_url };
      }
      if(dynamic_path){
        richObject.button = {
          subType: "url",
          params: [
            {
              data: dynamic_path
            }
          ]
        };
      }
      requestBody["data"]["message_template"]["rich_template_data"] = richObject;
    } else {
      requestBody["data"]["message_template"]["template_data"] = dataArray;
    }
    console.log('OMS requestBody for whatsapp msg :::::', JSON.stringify(requestBody));
    return { requestBody, error: false };
  }

  module.exports = getWpMessage;