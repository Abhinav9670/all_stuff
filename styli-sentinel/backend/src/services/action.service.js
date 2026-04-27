const actionModel = require('../models/action.model');
const serviceModel = require('../models/service.model');

exports.createServiceAction = async (body) => {
  try {
    const {
      _action,
      domain,
      name,
      url,
      httpMethod,
      type,
      description = '',
      authorization = true,
      authentication = true,
      status = true,
    } = body;
    const fullUrl = `${httpMethod.toUpperCase()}|${domain}${url}`;
    if (_action == 'add') {
      const actionCount = await actionModel.count({ _id: fullUrl });
      if (actionCount > 0) {
        return { code: 400, message: 'Action for the Domain Already Present.' };
      }
      const actionDoc = new actionModel({
        _id: fullUrl,
        domain,
        name,
        url,
        httpMethod,
        type,
        description,
        authorization,
        authentication,
        status,
      });
      await actionDoc.save();
      return {
        code: 200,
        message: 'Action for Service Domain Created Successfully.',
      };
    }
    if (_action == 'update') {
      const updatedActionDoc = await actionModel.findByIdAndUpdate(
        fullUrl,
        {
          domain,
          name,
          url,
          httpMethod,
          type,
          description,
          authorization,
          authentication,
          status,
        },
        { new: true }
      );
      if (!updatedActionDoc) {
        return { code: 400, error: 'Action For Service Domain not found' };
      }
      return {
        code: 200,
        message: 'Action For Service Domain Updated Successfully.',
      };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.getServiceAction = async (body) => {
  try {
    const { _id, pagination = {}, filter = {} } = body;
    if (_id) {
      const actionDoc = await actionModel.findOne({ _id: _id });

      if (!actionDoc) {
        return res.status(404).json({ error: 'Service Domain not found' });
      }
      return {
        code: 200,
        message: 'Action Fetched Successfully.',
        data: { action: actionDoc },
      };
    }

    const { page = 1, pageSize = 10 } = pagination;

    const constructedFilter = { ...filter };

    if (filter.url) {
      constructedFilter.url = { $regex: filter.url, $options: 'i' };
    }

    const skipCount = (page - 1) * pageSize;
    const totalCount = await actionModel.countDocuments(constructedFilter);
    const actions = await actionModel
      .find(constructedFilter)
      .skip(skipCount)
      .limit(pageSize)
      .exec();
    return {
      code: 200,
      message: 'Actions List Fetched Successfully.',
      data: { actions: actions, totalCount, page, pageSize },
    };
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.removeServiceAction = async (body) => {
  try {
    const { _id } = body;
    if (_id) {
      const deletedAction = await actionModel.deleteOne({ _id: _id });
      if (deletedAction?.deletedCount === 0) {
        return res.status(404).json({ error: "Action doesn't exists" });
      }
      return { status: 200, message: 'Action URL removed successfully' };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.statusUpdateServiceAction = async (body) => {
  try {
    const { _id, column } = body;
    if (_id && column) {
      const actionDoc = await actionModel.findOne({ _id: _id });
      if (!actionDoc) {
        return {
          status: 404,
          message: 'Invalid Action or url service domain',
        };
      }
      const valToBeUpdated = !actionDoc[column];
      let update = {};
      update[column] = valToBeUpdated;
      await actionModel.updateMany({ _id: _id }, [{ $set: update }]);
      return {
        status: 200,
        message: 'Service Domain Status updated successfully',
      };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.bulkUploadActions = async (actions) => {
  try {
    const newServices = [];
    const newActions = [];
    for (const action of actions) {
      if (!newServices.find((e) => e._id == action.domain)) {
        newServices.push({
          _id: action.domain,
          name: action.domainName,
        });
      }

      if (!newActions.find((e) => e._id == getFullUrl(action))) {
        newActions.push({
          _id: getFullUrl(action),
          domain: action.domain,
          name: action.name,
          url: action.url,
          httpMethod: action.httpMethod,
          type: action.type,
          description: action.description,
        });
      }
    }
    const options = { ordered: false };
    if (newServices.length > 0) {
      try {
        await serviceModel.insertMany(newServices, options);
      } catch (error) {
        console.log(error);
      }
    }
    if (newActions.length > 0) {
      try {
        await actionModel.insertMany(newActions, options);
      } catch (error) {
        console.log(error);
      }
    }
    return { status: 200, message: 'Bulk Uploaded successfully' };
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

exports.csvUploadActions = async (jsonArray) => {
  // Create a new document with the CSV data
  const newActions = [];
  const options = { ordered: false };
  jsonArray.forEach(async (row) => {
    newActions.push({
      action_name: row.action_name,
      action_service_name: row.action_service_name,
      action_valid: row.action_valid.toLowerCase() === 'true',
      action_url: row.action_url,
    });
  });

  // Save the document to MongoDB
  try {
    await actionModel.insertMany(newActions, options);
    return { status: 200, message: 'Bulk Uploaded successfully' };
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`,
    };
  }
};

const getFullUrl = (action) => {
  return `${action.httpMethod.toUpperCase()}|${action.domain}${action.url}`;
};
