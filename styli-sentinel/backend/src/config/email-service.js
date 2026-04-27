const sgMail = require('@sendgrid/mail');


const logInfo = (key, message) => {
    try {
      const payload = {
        key: typeof key === 'object' ? JSON.stringify(key) : key,
        message: typeof message === 'object' ? JSON.stringify(message) : message
        // request: {
        //   headers: {
        //     'x-header-token': xHeaderToken
        //   }
        // }
      };
      console.log(JSON.stringify(payload));
    } catch (e) {
      console.error('error info  Logging ', e.message);
    }
  };

/**
 * Send an email
 * @param {string} to
 * @param {string} subject
 * @param {string} text
 * @return {Promise}
 */
const sendEmail = async ({ to, from, subject, html, attachments = [] }) => {
  try {
    const toArray = to?.split(',');
    const sendGridApiKey = global?.globalConfig?.emailConfig?.sendGridApiKey;
    const { fromEmail, fromName } = global?.globalConfig?.emailConfig || {};

    sgMail.setApiKey(sendGridApiKey);
    const msg = {
      to: toArray, // Change to your recipient
      from: from || { email: fromEmail, name: fromName }, // Change to your verified sender
      subject,
      isMultiple: true,
      html,
      attachments
    };

    // const res = await sgMail.send(msg);
    const res = await sgMail.sendMultiple(msg);
    logInfo({ res, subject, to });

    return true;
  } catch (e) {
    console.log('SENDGRID', global?.globalConfig?.emailConfig?.sendGridApiKey, JSON.stringify(e))
    global.logError(e);
    return false;
  }

  //   await transport.sendMail(msg);
};

module.exports = {
  sendSgEmail: sendEmail
};
