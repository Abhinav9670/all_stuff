const { insertOne, fetchDocs, fetchDocCount } = require('../utils/mongo');

exports.addAdminLog = async ({
  type,
  data,
  email,
  isFileUpload = false,
  desc
}) => {
  if (!type || !data) {
    throw new Error('type and data are mandatory for logging');
  }

  try {
    // customer
    const typeArray = ['configUpdate', 'customer', 'order'];
    if (!typeArray.includes(type)) {
      data = JSON.stringify(data);
    }
    insertOne({
      collection: 'adminLogs',
      data: {
        desc,
        type,
        data,
        email,
        isFileUpload,
        createdAt: new Date()
      }
    });
  } catch (e) {
    console.log('Error saving admin log!');
    console.log(e.message);
  }
};

exports.fetchAdminLogs = async ({ filters, offset, pagesize, query }) => {
  if (query) {
    filters.$or = [{ email: `${query}` }, { data: { $regex: `${query}` } }];
  }
  const result = await fetchDocs({
    collection: 'adminLogs',
    filters,
    offset,
    sort: { createdAt: -1 },
    pagesize
  });
  const count = await fetchDocCount({ collection: 'adminLogs', filters });
  return { result, totalCount: count };
};
