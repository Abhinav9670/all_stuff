const express = require('express');
// const auth = require('../../middlewares/auth');
const validate = require('../../middlewares/validate');
const userValidation = require('../../validations/user.validation');
const userController = require('../../controllers/user.controller');
const authValidate = require('../../middlewares/authValidate');

const router = express.Router();

router
  .route('/')
  .post(
    authValidate(),
    validate(userValidation.createUser),
    userController.createUser
  )
  .get(
    authValidate(),
    validate(userValidation.getUsers),
    userController.getUsers
  );

router
  .route('/:userId')
  .get(authValidate(), validate(userValidation.getUser), userController.getUser)
  .patch(
    authValidate(),
    validate(userValidation.updateUser),
    userController.updateUser
  )
  .delete(
    authValidate(),
    validate(userValidation.deleteUser),
    userController.deleteUser
  );

module.exports = router;
