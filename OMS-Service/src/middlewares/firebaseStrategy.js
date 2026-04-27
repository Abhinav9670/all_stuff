const { Strategy, ExtractJwt } = require('passport-firebase-jwt');
const { auth } = require('firebase-admin');
const { Strategy: PassportStrategy } = require('passport-strategy');
console.log('PassportStrategy', PassportStrategy);

/**
 * @param {object} Strategy
 */
class FirebaseAuthStrategy extends PassportStrategy(Strategy) {
  /**
   *
   */
  constructor() {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken()
    });
  }

  /**
   *
   * @param {*} token
   * @return{*}
   */
  validate(token) {
    return auth()
      .verifyIdToken(token, true)
      .catch(err => {
        global.logError(err);
        // throw new UnauthorizedException();
      });
  }
}

exports.FirebaseAuthStrategy = FirebaseAuthStrategy;
