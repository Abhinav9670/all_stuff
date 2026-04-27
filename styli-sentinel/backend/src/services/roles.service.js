const roleModel = require('../models/role.model');

exports.createRole = async (body) => {
  try {
    const { _action, _id, name, description, service, action, status } = body;
    if (_action == 'add') {
      const roleDoc = new roleModel({
        name,
        description,
        service,
        action,
        status,
      });
      await roleDoc.save();
      return { code: 200, message: 'Role Created Successfully.' };
    }
    if (_action == 'update') {
      const updatedRoleDoc = await roleModel.findByIdAndUpdate(
        _id,
        {
          name,
          description,
          service,
          action,
          status,
        },
        { new: true }
      );

      if (!updatedRoleDoc) {
        return { code: 400, error: 'Role not found' };
      }
      return { code: 200, message: 'Role Updated Successfully.' };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.getRoles = async (body) => {
  try {
    const { _id, pagination = {}, filter = {} } = body;
    if (_id) {
      const roleDoc = await roleModel.findOne({ _id: _id });

      if (!roleDoc) {
        return res.status(404).json({ error: 'Role not found' });
      }
      return {
        code: 200,
        message: 'Role Fetched Successfully.',
        data: { role: roleDoc },
      };
    }
    const { page = 1, pageSize = 10 } = pagination;

    const skipCount = (page - 1) * pageSize;
    const totalCount = await roleModel.countDocuments(filter);
    const roles = await roleModel
      .find(filter)
      .skip(skipCount)
      .limit(pageSize)
      .exec();
    return {
      code: 200,
      message: 'UserFetched Successfully.',
      data: { role: roles, totalCount, page, pageSize },
    };
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.removeRole = async (body) => {
  try {
    const { _id } = body;
    if (_id) {
      const deletedRole = await roleModel.deleteOne({ _id: _id });
      if (deletedRole?.deletedCount === 0) {
        return res.status(404).json({ error: "Role doesn't exists" });
      }
      return { status: 200, message: 'Role removed successfully' };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};
