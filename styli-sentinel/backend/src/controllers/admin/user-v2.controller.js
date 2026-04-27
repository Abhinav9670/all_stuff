const httpStatus = require('http-status');
const { createUserV2, updateUserV2 } = require('../../services/users-v2.service');

exports.userAddV2 = async ({ res, req }) => {
    try {
        const { body } = req;
        const results = await createUserV2(body);
        if (results) {
            res.status(httpStatus.OK).send(JSON.stringify(results));
        }
    } catch (e) {
        global.logError(e);
        res.status(httpStatus.OK).send(e.message);
    }
};

exports.userUpdateV2 = async ({ res, req }) => {
    try {
        const { body } = req;
        const results = await updateUserV2(body);
        if (results) {
            res.status(httpStatus.OK).send(JSON.stringify(results));
        }
    } catch (e) {
        global.logError(e);
        res.status(httpStatus.OK).send(e.message);
    }
};
