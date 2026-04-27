const userModel = require('../models/user.model');
const { createFirebaseUser, updateFirebaseUser, firebaseHandlerForCreateUser, firebaseHandlerForUpdateUser } = require('../helper/firebase');
const { getFirebaseUserByEmail, updateFirebasePasswordWithUid } = require('../config/firebase-admin');

exports.createUserV2 = async (body) => {
    const {
        _id,
        password,
        name,
        roles,
        userType,
        authorization = true,
        authentication = true,
        status = true,
    } = body

    let { response, firebaseUser } = await firebaseHandlerForCreateUser({ _id, password })

    if (!response?.email) {
        return { code: 400, error: response?.error };
    } else {
        let userDoc = await userModel.findById(_id);
        if (userDoc) {
            const userRoles = userDoc.roles;
            const updatedRoles = userRoles.filter((role) => !roles.includes(role)).concat(roles);
            await userModel.findByIdAndUpdate(_id, { roles: updatedRoles });
        }
        else {
            userDoc = new userModel({
                _id,
                name,
                userType,
                roles,
                authentication,
                authorization,
                status
            });
            await userDoc.save();
        }

        if (!firebaseUser?.email) {
            firebaseUser = await getFirebaseUserByEmail(_id)
        }
        return { code: 200, message: 'User Created Successfully.', firebaseUserId: firebaseUser?.uid };
    }
}

exports.updateUserV2 = async (body) => {
    const {
        _id,
        oldPassword,
        password,
        isPasswordUpdated,
        name,
        oldRoles,
        roles,
        authorization = true,
        authentication = true,
        status = true,
    } = body

    let { response, firebaseUser } = await firebaseHandlerForUpdateUser({ _id, oldPassword, password, isPasswordUpdated })
    if (!firebaseUser?.email) {
        return { code: 400, error: response?.error };
    }

    let userDoc = await userModel.findById(_id);
    console.log('found userDoc', userDoc)
    if (userDoc) {
        const userRoles = userDoc.roles;
        const updatedRoles = userRoles.filter((role) => !roles.includes(role) && !oldRoles.includes(role)).concat(roles);
        await userModel.findByIdAndUpdate(_id, { roles: updatedRoles });
    }
    else {
        userDoc = new userModel({
            _id,
            name,
            roles,
            authentication,
            authorization,
            status
        });
        await userDoc.save();
    }
    if (!firebaseUser?.email) {
        firebaseUser = await getFirebaseUserByEmail(_id)
    }
    console.log('firebaseUser', firebaseUser)
    return { code: 200, message: 'User Updated Successfully.', firebaseUserId: firebaseUser?.uid };
}