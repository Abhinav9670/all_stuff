const httpStatus = require('http-status');
const ApiError = require('../../utils/ApiError');
const mongoUtil = require('../../utils/mongoInit');
const slugify = require('slugify');

const getPermissionGroups = async () => {
  const db = mongoUtil.getDb();

  try {
    const groups = await db.collection('groups').find().toArray();
    const data = [];
    for (const el of groups) {
      data.push({
        id: el._id,
        name: el.name,
        description: el.description,
        permissions: el.permissions
      });
    }
    return data;
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Fetching permission groups failed'
    );
  }
};

const savePermissionGroup = async ({ group }) => {
  const db = mongoUtil.getDb();

  try {
    group.updated_at = new Date();
    group._id =
      group._id || slugify(group.name.replace(/-/g, '_').toLowerCase(), '_');
    return await db
      .collection('groups')
      .updateOne({ _id: group._id }, { $set: group }, { upsert: true });
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Permission Group saving failed'
    );
  }
};

const deletePermissionGroup = async ({ id }) => {
  const db = mongoUtil.getDb();

  try {
    return await db.collection('groups').remove({ _id: id });
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Permission Group deleting failed'
    );
  }
};

module.exports = {
  getPermissionGroups,
  savePermissionGroup,
  deletePermissionGroup
};
