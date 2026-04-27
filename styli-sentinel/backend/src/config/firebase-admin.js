const firebaseAdmin = require('firebase-admin');
const { getAuth } = require('firebase-admin/auth');



firebaseAdmin.initializeApp({
  credential: firebaseAdmin.credential.applicationDefault()
});
const firebaseAdminAuth = getAuth();

const getFirebaseUserByEmail = async (email) => {
  let user
  try {
    user = await firebaseAdmin.auth().getUserByEmail(email)
  } catch (err) {
    console.log('getFirebaseUserByEmail err', err)
  }
  return user
}

const updateFirebasePasswordWithUid = async (uId, password) => firebaseAdmin.auth().updateUser(uId, {
  password
})


module.exports = {
  firebaseAdminAuth,
  getFirebaseUserByEmail,
  updateFirebasePasswordWithUid
};