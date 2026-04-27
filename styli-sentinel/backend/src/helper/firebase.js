const firebase_init = process.env.FIREBASE_APIKEY

const axios = require('axios');
const { getFirebaseUserByEmail, updateFirebasePasswordWithUid } = require('../config/firebase-admin');

async function createFirebaseUser(email, password) {//used to create firebase user
    try {
        let url = 'https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=' + firebase_init;
        let response = await axios.post(url, { email, password })
        console.log('user created', response)
        return response?.data
    } catch (error) {
        console.error('Error Message:', error);  // Error message or body from the server
        return { "error": error.response.data, "status": false };
    }
}

async function updateFirebaseUser(email, password, oldPassword) {
    try {
        console.log('updateFirebaseUser email', email)
        console.log('updateFirebaseUser password', password)
        const loginResponse = await loginFirebaseUser(email, oldPassword);
        console.log('loginResponse', loginResponse)
        if (!loginResponse.error) {
            let { idToken } = loginResponse
            console.log('firebase idToken', idToken)
            const url = 'https://identitytoolkit.googleapis.com/v1/accounts:update?key=' + firebase_init;
            const response = await axios.post(url, { idToken, email, password })
            return response.data;
        }
        return loginResponse;
    } catch (error) {
        console.error('Error Message:', error);
        return { "message": error.response.data, "status": false };
    }
}

async function loginFirebaseUser(email, password) {//used to create firebase user
    try {
        const url = 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=' + firebase_init;
        const response = await axios.post(url, { email, password })
        console.log('loginFirebaseUser response', response)
        return response.data;

    } catch (error) {
        console.error('Error Message:', error);  // Error message or body from the server
        return { "error": error, "status": false };
    }
}

const firebaseHandlerForCreateUser = async ({ _id, password }) => {
    let firebaseUser = await getFirebaseUserByEmail(_id)
    console.log('firebaseUser', firebaseUser)

    let response
    if (!firebaseUser?.uid) {
        // Create firebase user if not present 
        response = await createFirebaseUser(_id, password);
        console.log('response', response)
    }
    else {
        // Update firebase password with uid if user already present 
        response = await updateFirebasePasswordWithUid(firebaseUser.uid, password)
        console.log('updateFirebasePasswordWithUid', response)
    }
    console.log('createfirebase response', response)
    return { response, firebaseUser }
}

const firebaseHandlerForUpdateUser = async ({ _id, password, oldPassword, isPasswordUpdated }) => {
    let firebaseUser = await getFirebaseUserByEmail(_id)
    console.log('firebaseUser', firebaseUser)
    let response
    if (firebaseUser?.uid) {
        // if firebase user already present
        if (isPasswordUpdated) {
            response = await updateFirebaseUser(_id, password, oldPassword);
            console.log('response', response)
        }
    }
    else {
        // create firebase user if not present
        response = await createFirebaseUser(_id, password);
    }
    return { response, firebaseUser }
}

module.exports = {
    createFirebaseUser,
    updateFirebaseUser,
    firebaseHandlerForCreateUser,
    firebaseHandlerForUpdateUser
}