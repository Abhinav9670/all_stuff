const passport = require('passport');

const httpStatus = require('http-status');

const { AbilityBuilder, Ability } = require('@casl/ability');
const mongoUtil = require('../utils/mongoInit');
const { permissionMap } = require('./permissionsMap');

/**
 * @param {string} email
 */
async function defineAbilitiesFor(email) {
  const { can, rules } = new AbilityBuilder();

  if ((global?.baseConfig?.superadmins || []).indexOf(email) > -1) {
    can('manage', 'all');
  } else if (email) {
    const db = mongoUtil.getDb();
    const userData = await db.collection('users').findOne({ email: email });
    if (!userData) return undefined;
    const permissionGroups = userData.permission_groups.map(el => el.value);

    await db
      .collection('groups')
      .find({ _id: { $in: permissionGroups } })
      .forEach(el => {
        // console.log('el', el);
        if (el.permissions.length) {
          el.permissions.forEach(permission => {
            // console.log('permission', permission);
            can(`${permission.type}`, `${permission.target}`);
          });
        }
      });
  } else {
    console.log('email not received for permissions calculations');
    return undefined;
  }

  return new Ability(rules);
}

const verifyCallback = (req, res, resolve) => async () => {
  // console.log(req.decodedToken, "------req.decodedToken-----");

  const { decodedToken } = req;
  let uid;
  let email;
  if (decodedToken) {
    uid = decodedToken.uid;
    email = decodedToken.email;
    req.email = email;
    req.uid = uid;
  }
  const ability = await defineAbilitiesFor(email);
  if (!ability) {
    return res.status(httpStatus.UNAUTHORIZED).json({
      status: true,
      statusCode: '401',
      statusMsg: 'Email not found/registered'
    });
  }

  if (global.baseConfig?.configs?.apisCheckACL) {
    const urlObject = permissionMap[req.originalUrl];
    const errorMessage = urlObject?.message || 'Unauthorized!';
    if (urlObject && ability.cannot(urlObject.action, urlObject.ability))
      return res.status(httpStatus.UNAUTHORIZED).send(errorMessage);

    // Get urls has url params so direct match for full urls not possible like what we can do for post urls.
    // Hence we will match the route path which has url param definition.
    // We have two types of checks prmarily coz originalUrl is better to match whole url
    // but route.path is just a fallback as originalUrl cannot be matched.

    // TODO: Perhaps we can improve on these checks to include the request type itself,
    // so we wll cover all urls in single logic.
    const pathObject = permissionMap[req.route.path];
    const errorMessageOnlyPath = urlObject?.message || 'Unauthorized!';
    if (pathObject && ability.cannot(pathObject.action, pathObject.ability))
      return res.status(httpStatus.UNAUTHORIZED).send(errorMessageOnlyPath);
  }

  // req = { ...req, ability };
  // req.ability = ability;

  const newReq = req;
  newReq.ability = ability;

  return resolve();
};

const auth = () => async (req, res, next) => {
  const { firebaseAuthValidation } = global?.baseConfig || {};
  if (firebaseAuthValidation) {
    return new Promise((resolve, reject) => {
      passport.authenticate(
        'fb',
        { session: false },
        // verifyCallback(req, res, resolve, reject, '')
        verifyCallback(req, res, resolve)
      )(req, res, next);
    })
      .then(() => next())
      .catch(err => next(err));
  } else {
    next();
  }
};

module.exports = auth;
