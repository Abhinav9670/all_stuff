const httpStatus = require('http-status');
const ApiError = require('../../utils/ApiError');
const mongoUtil = require('../../utils/mongoInit');
const slugify = require('slugify');

const getTeams = async () => {
  const db = mongoUtil.getDb();

  try {
    const teams = await db.collection('teams').find().toArray();
    const data = [];
    for (const el of teams) {
      data.push(el);
    }
    return data;
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Fetching teams failed'
    );
  }
};

const saveTeam = async ({ team }) => {
  const db = mongoUtil.getDb();

  try {
    team.updated_at = new Date();
    team._id =
      team._id || slugify(team.name.replace(/-/g, '_').toLowerCase(), '_');
    return await db
      .collection('teams')
      .updateOne({ _id: team._id }, { $set: team }, { upsert: true });
  } catch (e) {
    throw new ApiError(httpStatus.INTERNAL_SERVER_ERROR, 'Team saving failed');
  }
};

const deleteTeam = async ({ id }) => {
  const db = mongoUtil.getDb();

  try {
    return await db.collection('teams').remove({ _id: id });
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Team deleting failed'
    );
  }
};

module.exports = {
  getTeams,
  saveTeam,
  deleteTeam
};
