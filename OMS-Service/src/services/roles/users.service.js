const httpStatus = require('http-status');
const ApiError = require('../../utils/ApiError');
const mongoUtil = require('../../utils/mongoInit');
const slugify = require('slugify');
const admin = require('../../config/firebase-admin-config');
const userCollection = 'users';

const createFirebaseUser = async ( user, userSlug) => {
  // firebase user creation required in case of create operation
  try {
    await admin.auth().createUser({
      email: user.email,
      emailVerified: false,
      password: user.password,
      displayName: user.name,
      disabled: false
    });
    user.updated_at = new Date();
    user._id = userSlug;
    const result = await updateUser(
      { _id: user._id },
      { $set: user },
      { upsert: true }
    );
    return { status: 'success', result: result };
  } catch (error) {
    if (error.code === 'auth/email-already-exists') {
      const existingUserWithEmail = await findUser({
        email: user.email
      });
      if (existingUserWithEmail.length) {
        return { status: 'error', result: error };
      } else {
        user.updated_at = new Date();
        user._id = userSlug;
        const result = await updateUser(
          { _id: user._id },
          { $set: user },
          { upsert: true }
        );
        return { status: 'success', result: result };
      }
    } else {
      return { status: 'error', result: error };
    }
  }
};

const getUsers = async () => {
  const db = mongoUtil.getDb();
  try {
    const users = await db
      .collection(userCollection)
      .find()
      .sort({ _id: -1 })
      .toArray();
    const data = [];
    for (const el of users) {
      delete el.password;
      data.push(el);
    }
    return data;
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Fetching users failed'
    );
  }
};

const findUser = async filters => {
  const db = mongoUtil.getDb();
  return await db.collection(userCollection).find(filters).toArray();
};

const updateUser = async (obj1, obj2, obj3) => {
  const db = mongoUtil.getDb();
  return await db.collection(userCollection).updateOne(obj1, obj2, obj3);
};

const checkSameNameUser = async user => {
  let existingUserWithSameName = false;

  const usersWithSameName = await findUser({ name: user.name });
  // If existing users with same name found
  if (usersWithSameName.length > 0) {
    // In case of update operation, skip self and check true
    if (user._id) {
      for (const userObject of usersWithSameName) {
        if (userObject._id !== user._id) existingUserWithSameName = true;
      }
    }
    // In case of create operation, checks true
    if (!user._id) existingUserWithSameName = true;

    return existingUserWithSameName;
  }
};

const saveUser = async ({ user }) => {
  const userSlug = slugify(user.name.replace(/-/g, '_').toLowerCase(), '_');
  try {
    const existingUserWithSameName = await checkSameNameUser(user);
    if (existingUserWithSameName) {
      return {
        status: 'error',
        result: { message: 'User with this name already exists.' }
      };
    }
    if (user._id) {
      const result = await updateUser(
        { _id: user._id },
        { $set: user },
        { upsert: true }
      );
      return { status: 'success', result: result };
    }
    return await createFirebaseUser(user, userSlug);
  } catch (e) {
    throw new ApiError(httpStatus.INTERNAL_SERVER_ERROR, 'User saving failed');
  }
};

const deleteUser = async ({ id }) => {
  const db = mongoUtil.getDb();

  try {
    return await db.collection(userCollection).remove({ _id: id });
  } catch (e) {
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'User deleting failed'
    );
  }
};

module.exports = {
  getUsers,
  saveUser,
  deleteUser
};
