const { Op } = require('sequelize');

exports.setExactMatch = (obj, k, v) => {
  return { ...obj, [k]: v };
};

exports.setNotMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: { [Op.ne]: v }
  };
};

exports.setLikeMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: {
      [Op.like]: `%${v}%`
    }
  };
};

exports.setInMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: { [Op.in]: v }
  };
};

exports.setGTMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: {
      ...(obj?.[k] || {}),
      [Op.gt]: v
    }
  };
};

exports.setLTMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: {
      ...(obj?.[k] || {}),
      [Op.lt]: v
    }
  };
};

exports.setGTEMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: {
      ...(obj?.[k] || {}),
      [Op.gte]: v
    }
  };
};

exports.setLTEMatch = (obj, k, v) => {
  return {
    ...obj,
    [k]: {
      ...(obj?.[k] || {}),
      [Op.lte]: v
    }
  };
};
