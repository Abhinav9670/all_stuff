const userModel = require('../models/user.model');

exports.createUser = async (body) => {
  try {
    const {
      _action,
      _id,
      name,
      roles,
      authorization = true,
      authentication = true,
      status = true,
    } = body;
    if (_action == 'add') {
      const userDoc = new userModel({
        _id: _id.toLowerCase(),
        name,
        roles,
        authorization,
        authentication,
        status,
      });
      await userDoc.save();
      return { code: 200, message: 'User Created Successfully.' };
    }
    if (_action == 'update') {
      const updatedUserDoc = await userModel.findByIdAndUpdate(
        _id,
        {
          name,
          roles,
          authorization,
          authentication,
          status,
        },
        { new: true }
      );

      if (!updatedUserDoc) {
        return { code: 400, error: 'User not found' };
      }
      return { code: 200, message: 'User Updated Successfully.' };
    }
  } catch (e) {
    global.logError(e);
    if (e?.code === 11000 && e?.keyValue?._id) {
      return {
        code: 409,
        message: `User Email already exists`,
      };
    } else {
      return {
        code: 500,
        message: `Something went wrong. Message: ${e.message}`,
      };
    }
  }
};

exports.getUsers = async (body) => {
  try {
    const { _id, pagination = {}, filter = {} } = body;
    if (_id) {
      const userDoc = await userModel.findOne({ _id: _id });

      if (!userDoc) {
        return res.status(404).json({ error: 'User not found' });
      }
      return {
        code: 200,
        message: 'User Fetched Successfully.',
        data: { user: userDoc },
      };
    }
    const { page = 1, pageSize = 10 } = pagination;

    const skipCount = (page - 1) * pageSize;
    const totalCount = await userModel.countDocuments(filter);
    const users = await userModel
      .find(filter)
      .skip(skipCount)
      .limit(pageSize)
      .exec();
    return {
      code: 200,
      message: 'UserFetched Successfully.',
      data: { user: users, totalCount, page, pageSize },
    };
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.removeUser = async (body) => {
  try {
    const { _id } = body;
    if (_id) {
      const deletedUser = await userModel.deleteOne({ _id: _id });
      if (deletedUser?.deletedCount === 0) {
        return res.status(404).json({ error: "User doesn't exists" });
      }
      return { status: 200, message: 'User removed successfully' };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};
