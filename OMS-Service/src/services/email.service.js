const sgMail = require('@sendgrid/mail');
const { logInfo } = require('../utils');

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
    const sendGridApiKey = global?.baseConfig?.emailConfig?.sendGridApiKey;
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    
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
    console.log('SENDGRID', global?.baseConfig?.emailConfig?.sendGridApiKey, JSON.stringify(e))
    global.logError(e);
    return false;
  }

  //   await transport.sendMail(msg);
};

module.exports = {
  sendSgEmail: sendEmail
};
