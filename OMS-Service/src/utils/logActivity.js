const mongoUtil = require('./mongoInit');

/**
 *
 * @param {*} obj
 * @return {*}
 */
function deepen(obj) {
  const result = {};

  // For each object path (property key) in the object
  for (const objectPath in obj) {
    // Split path into component parts
    const parts = objectPath.split('.');

    // Create sub-objects along path as needed
    let target = result;
    while (parts.length > 1) {
      const part = parts.shift();
      target = target[part] = target[part] || {};
    }

    // Set value at end of path
    target[parts[0]] = obj[objectPath];
  }

  return result;
}

// function difference(object, base) {
//   function changes(object, base) {
//     return _.transform(object, function (result, value, key) {
//       if (!_.isEqual(value, base[key])) {
//         result[key] =
//           _.isObject(value) && _.isObject(base[key])
//             ? changes(value, base[key])
//             : value;
//       }
//     });
//   }

//   return changes(object, base);
// }

/**
 * Async logActivity
 * @param {*} collection
 * @param {*} before
 * @param {*} after
 * @param {*} key
 * @return {Object}
 */
const logActivity = async (
  collection,
  before,
  after,
  key,
  { email, name, picture, bulkId, isBulk = false, isSystem = false }
) => {
  try {
    if (!email && process.env.NODE_ENV !== 'production') {
      global.logError('For logging, email and name is mandatory');
      process.exit(1);
    }
    // Async Save activity in Mongo Collection;
    const db = mongoUtil.getDb();

    // Find before doc
    // let beforeDoc = await db.collection(collection).find({_id: key}).toArray();
    // let before = beforeDoc[0];

    // console.log(difference(beforeDoc, after), "Difference")
    after = deepen(after);
    const result = await db.collection('AdminActivity').insertOne({
      collection,
      before,
      after,
      addedOn: new Date(),
      key,
      email,
      name,
      picture,
      bulkId,
      isSystem,
      isBulk
    });
    return result.insertedId;
  } catch (e) {
    global.logError('----Error@logActivity----', e.message);
    return false;
  }
};

module.exports = logActivity;
