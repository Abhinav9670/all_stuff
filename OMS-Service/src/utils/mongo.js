const { isEmpty } = require('lodash');
const mongoUtil = require('../utils/mongoInit');

exports.insertOne = async ({ collection, data }) => {
  const db = mongoUtil.getDb();
  if (!db) {
    // Return null instead of throwing error to prevent app crashes
    // The error will be logged in addAdminLog's catch block
    console.log('MongoDB database connection is not available - skipping log insert');
    return null;
  }
  return await db
    .collection(collection)
    .insertOne(data)
    .then(result => {
      return result;
    });
};

exports.updateOne = async ({ collection, filter, update }) => {
  const db = mongoUtil.getDb();
  return await db
    .collection(collection)
    .updateOne(filter, { $set: update })
    .then(result => {
      return result;
    });
};

const prepareDateFilter = ({ fromDate, toDate } = {}) => {
  const filter = {};
  if (fromDate) {
    filter.$gte = new Date(fromDate);
  }
  if (toDate) {
    filter.$lte = new Date(toDate);
  }
  return filter;
};

exports.fetchDocs = async ({
  collection,
  filters,
  sort = {},
  offset = 0,
  pagesize = 10
}) => {
  const db = mongoUtil.getDb();
  const skip = offset ? offset * pagesize : 0;
  filters.createdAt = prepareDateFilter(filters.createdAt);

  if (isEmpty(filters.createdAt)) {
    delete filters.createdAt;
  }

  return await db
    .collection(collection)
    .find(filters)
    .sort(sort)
    .skip(skip)
    .limit(pagesize)
    .toArray();
};

exports.fetchDocCount = async ({ collection, filters }) => {
  const db = mongoUtil.getDb();
  return await db.collection(collection).find(filters).count();
};
