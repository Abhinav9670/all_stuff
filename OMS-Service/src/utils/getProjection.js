// Returns projection for Mongo query.
const getProjection = arr => {
  const ret = {};
  arr.map(v => {
    ret[v] = 1;
  });

  return ret;
};

module.exports = getProjection;
