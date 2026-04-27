const momentTimeZone = require('moment-timezone');
const momentZone = 'Asia/Riyadh';
const momentFormat = 'YYYY-MM-DD HH:mm:ss';

exports.getKSATime = date => {
  return !date
    ? undefined
    : momentTimeZone(date).tz(momentZone).format(momentFormat);
};
