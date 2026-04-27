const crypto = require('crypto');

const createHash = (string) => {
  return crypto.createHash('sha256').update(string).digest('hex');
};

const generateSignature = (passphrase, object) => {
  let signatureText = '';
  const keys = [];

  for (const eachKey in object) {
    keys.push(eachKey);
  }
  keys.sort(compare);
  for (const k of keys) {
    let value = object[k];
    if (typeof object[k] === 'object') {
      if (Array.isArray(object[k])) {
        value = object[k]
          .map(b =>
            typeof b === 'string' ? b : JSON.stringify(b).replace(/"/g, '').replace(/:/g, '=').replace(/,/g, ', ')
          )
          .join(', ');
        value = `[${value}]`;
      } else {
        value = JSON.stringify(object[k]).replace(/"/g, '').replace(/:/g, '=').replace(/,/g, ', ');
      }
    }
    signatureText = signatureText + (k + '=' + value);
  }
  console.log('signatureText::passphrase', passphrase, signatureText);
  return createHash(passphrase + signatureText + passphrase);
};

function compare(a, b) {
  if (a < b) return -1;
  if (a > b) return 1;
  return 0;
}

exports.generateSignature = generateSignature;
