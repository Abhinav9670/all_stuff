const actionModel = require('../models/action.model');
const serviceModel = require('../models/service.model');

exports.createService = async body => {
  try {
    const {
      _action,
      domain,
      name,
      description = '',
      authorization = true,
      authentication = true,
      status = true,
      verifyotp = true
    } = body;
    if (_action == 'add') {
      const serviceCount = await serviceModel.count({ _id: domain });
      if (serviceCount > 0) {
        return { code: 400, message: 'Domain Already Present.' };
      }
      const serviceDoc = new serviceModel({
        _id: domain,
        name,
        description,
        authorization,
        authentication,
        status,
        verifyotp
      });
      await serviceDoc.save();
      return { code: 200, message: 'Service Domain Created Successfully.' };
    }
    if (_action == 'update') {
      const updatedServiceDoc = await serviceModel.findByIdAndUpdate(
        domain,
        {
          name,
          description,
          authorization,
          authentication,
          status,
          verifyotp
        },
        { new: true }
      );

      if (!updatedServiceDoc) {
        return { code: 400, error: 'Service Domain not found' };
      }
      return { code: 200, message: 'Service Domain Updated Successfully.' };
    }
  } catch (e) {
    global.logError(e);
    return {
      code: 500,
      message: `Something went wrong. Message: ${e.message}`
    };
  }
};

exports.getService = async body => {
  try {
    const { _id, pagination = {}, filter = {} } = body;
    if (_id) {
      const serviceDoc = await serviceModel.findOne({ _id: _id });

      if (!serviceDoc) {
        return res.status(404).json({ error: 'Service Domain not found' });
      }
      return {
        code: 200,
        message: 'Service Domain Fetched Successfully.',
        data: { service: serviceDoc }
      };
    }
    const { page = 1, pageSize = 10 } = pagination;

    const skipCount = (page - 1) * pageSize;
    const totalCount = await serviceModel.countDocuments();
    const services = await serviceModel.find().skip(skipCount).limit(pageSize).exec();
    return {
      code: 200,
      message: 'Service Domains Fetched Successfully.',
      data: { services: services, totalCount, page, pageSize }
    };
  } catch (error) {
    global.logError(error);
    return {
      code: 500,
      message: `Something went wrong. Message: ${error.message}`
    };
  }
};

exports.removeService = async body => {
  try {
    const { _id } = body;
    if (_id) {
      const deletedService = await serviceModel.deleteOne({ _id: _id });
      if (deletedService?.deletedCount === 0) {
        return res.status(404).json({ error: "Service Domain doesn't exists" });
      }
      return { status: 200, message: 'Service Domain removed successfully' };
    }
  } catch (error) {
    global.logError(error);
    return {
      code: 500,
      message: `Something went wrong. Message: ${error.message}`
    };
  }
};

exports.statusUpdateService = async body => {
  try {
    const { _id, column } = body;
    if (_id && column) {
      const serviceDoc = await serviceModel.findOne({ _id: _id });
      if (!serviceDoc) {
        return {
          status: 404,
          message: 'Invalid Service Domain'
        };
      }
      const valToBeUpdated = !serviceDoc[column];
      let update = {};
      update[column] = valToBeUpdated;
      await serviceModel.findOneAndUpdate({ _id: _id }, [{ $set: update }]);
      // await actionModel.updateMany({ domain: _id }, [{ $set: update }]);

      return {
        status: 200,
        message: 'Service Domain Status updated successfully'
      };
    }
  } catch (error) {
    global.logError(error);
    return {
      code: 500,
      message: `Something went wrong. Message: ${error.message}`
    };
  }
};
