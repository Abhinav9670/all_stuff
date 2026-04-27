import React, { useEffect } from 'react';
import { Row, Card, CardTitle, Label, FormGroup, Button } from 'reactstrap';
import { NavLink } from 'react-router-dom';
import { connect } from 'react-redux';
import { Formik, Form, Field } from 'formik';
import { NotificationManager } from '../../components/common/react-notifications';
import { Colxx } from '../../components/common/CustomBootstrap';
import IntlMessages from '../../helpers/IntlMessages';
import { getCurrentUser } from '../../helpers/Utils';
import { adminRoot } from '../../constants/defaultValues';
import { verifyOTP } from '../../redux/actions';

const VerifyOtp = ({ history, loading, error, verifyOtpUser }) => {
  useEffect(() => {
    if (error) {
      NotificationManager.warning(error, 'OTP Verification Error', 3000, null, null, '');
    }
  }, [error]);

  const initialValues = { otp: '' };

  const validateOTP = (value) => {
    let error;
    if (!value) {
      error = 'Please enter your OTP';
    } else if (!/^\d{6}$/.test(value)) {
      error = 'OTP must be a 6-digit number';
    }
    return error;
  };

  const onOtpSubmit = (values) => {
    console.log('Submitting OTP:', values.otp);
    verifyOtpUser(values, history);
  };

  useEffect(() => {
    const loggedInUser = getCurrentUser();
    if (loggedInUser) {
      history.push(adminRoot);
    }
  }, [history]);

  useEffect(() => {
    const userEmail = window.localStorage.getItem('email');
    if (!userEmail) {
      history.push('/user/login');
    }
  }, [history]);

  return (
    <Row className="h-100">
      <Colxx xxs="12" md="10" className="mx-auto my-auto">
        <Card className="auth-card">
          <div
            className="position-relative"
            style={{
              color: 'rgb(42, 22, 21)',
              width: '41%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              height: '400px',
              justifyContent: 'center'
            }}
          >
            <img src="/assets/logos/white-full.svg" width="120px" alt="Logo" />
            <p className="h2">Sentinal</p>
            <p className="mb-0" style={{ textAlign: 'center' }}>
              Please enter the OTP sent to your registered EMAIL ID.
            </p>
          </div>
          <div className="form-side">
            <NavLink to="/" className="white">
              <span className="logo-single" style={{ width: '60px' }} />
            </NavLink>
            <CardTitle className="mb-4">
              <IntlMessages id="user.verify-otp" />
            </CardTitle>

            <Formik initialValues={initialValues} onSubmit={onOtpSubmit}>
              {({ errors, touched, handleChange, values }) => (
                <Form className="av-tooltip tooltip-label-bottom">
                  <FormGroup className="form-group has-float-label">
                    <Label>
                      <IntlMessages id="user.enter-otp" />
                    </Label>
                    <Field
                      className="form-control"
                      name="otp"
                      validate={validateOTP}
                      value={values.otp}
                      onChange={(e) => {
                        const { value } = e.target;
                        if (/^\d{0,6}$/.test(value)) { // Allow only digits and limit to 6 characters
                          handleChange(e);
                        }
                      }}
                    />
                    {errors.otp && touched.otp && <div className="invalid-feedback d-block">{errors.otp}</div>}
                  </FormGroup>
                  <div className="d-flex justify-content-between align-items-center">
                    <div className="d-flex">
                      <Button
                        color="primary"
                        className={`btn-shadow btn-multiple-state ${loading ? 'show-spinner' : ''}`}
                        size="lg"
                        type="submit"
                      >
                        <span className="spinner d-inline-block">
                          <span className="bounce1" />
                          <span className="bounce2" />
                          <span className="bounce3" />
                        </span>
                        <span className="label">
                          <IntlMessages id="user.otp-submit" />
                        </span>
                      </Button>
                    </div>
                  </div>
                </Form>
              )}
            </Formik>
          </div>
        </Card>
      </Colxx>
    </Row>
  );
};

const mapStateToProps = ({ authUser }) => {
  const { loading, error } = authUser;
  return { loading, error };
};

export default connect(mapStateToProps, {
  verifyOtpUser: verifyOTP
})(VerifyOtp);
