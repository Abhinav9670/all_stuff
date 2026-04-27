const firebase = require('firebase-admin');

const admin = firebase.initializeApp({
  credential: firebase.credential.applicationDefault(),
});

module.exports = admin;
