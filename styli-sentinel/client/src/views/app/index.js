import React, { Suspense, useState, useEffect } from 'react';
import { Route, withRouter, Switch, Redirect } from 'react-router-dom';
import { connect } from 'react-redux';

import AppLayout from '../../layout/AppLayout';
import { setCurrentUser, getCurrentUser, removeFromCookie } from '../../helpers/Utils';
import axios from 'axios';
import { createNotification } from '../../costumePages/costumComponents/Notifications';
// import { ProtectedRoute, UserRole } from '../../helpers/authHelper';

const Gogo = React.lazy(() => import(/* webpackChunkName: "viwes-gogo" */ './gogo'));
const Services = React.lazy(() => import(/* webpackChunkName: "viwes-gogo" */ '../../costumePages/Services'));
const Roles = React.lazy(() => import(/* webpackChunkName: "viwes-gogo" */ '../../costumePages/Roles'));
const Users = React.lazy(() => import(/* webpackChunkName: "viwes-gogo" */ '../../costumePages/Users'));
const LoginHistory = React.lazy(() => import(/* webpackChunkName: "viwes-gogo" */ '../../costumePages/LoginHistory'));
const Actions = React.lazy(() => import(/* webpackChunkName: "viwes-gogo" */ '../../costumePages'));
const SecondMenu = React.lazy(() => import(/* webpackChunkName: "viwes-second-menu" */ './second-menu'));
const BlankPage = React.lazy(() => import(/* webpackChunkName: "viwes-blank-page" */ './blank-page'));

const App = ({ match }) => {
  const [user, setUser] = useState(undefined);
  const refreshFirebaseToken = async () => {
    console.log('Refreshing Firebase Token...');
    const loggedInUser = getCurrentUser();
    if (!loggedInUser) {
      window.location.href = '/user/login';
    }
    try {
      const { refreshToken, token } = loggedInUser;
      const result = await axios.post(
        `${process.env.SNTNL_API_HOST}api/v1/auth/regenerate-token`,
        {
          refreshToken
        },
        {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
          }
        }
      );
      let authUser = result?.data?.data;
      if (result?.data?.status) {
        authUser = {
          uuid: authUser?.uuid,
          name: authUser?.displayName,
          displayName: authUser?.displayName,
          email: authUser?.email,
          emailVerified: authUser?.registered,
          token: authUser?.token,
          refreshToken: authUser?.refreshToken
        };
        setCurrentUser(authUser);
        setUser(authUser);
      } else {
        removeFromCookie();
        setUser(undefined);
      }
    } catch (e) {
      removeFromCookie();
      setUser(undefined);
      console.log(e?.message);
      if (400 === e?.response?.status) {
        createNotification({
          type: 'error',
          title: 'Autherization Error',
          subtitle: `Token was expired.Please login again`
        });
      }
      window.location.href = '/user/login';
    }
  };
  useEffect(() => {
    console.log('useEffect is triggered!');
    // Call refreshFirebaseToken when the component mounts
    refreshFirebaseToken();
    const intervalId = setInterval(() => {
      refreshFirebaseToken(); // Call the function here
    }, 10 * 60 * 1000);

    // Cleanup interval when the component unmounts
    return () => {
      clearInterval(intervalId);
    };
  }, []);
  return (
    <AppLayout>
      <div className="dashboard-wrapper">
        {!user ? (
          <div className="loading" />
        ) : (
          <Suspense fallback={<div className="loading" />}>
            <Switch>
              <Redirect exact from={`${match.url}/`} to={`${match.url}/home`} />
              <Route path={`${match.url}/gogo`} render={props => <Gogo {...props} />} />
              <Route path={`${match.url}/home`} render={props => <Services {...props} />} />
              <Route path={`${match.url}/actions`} render={props => <Actions {...props} />} />
              <Route path={`${match.url}/roles`} render={props => <Roles {...props} />} />
              <Route path={`${match.url}/users`} render={props => <Users {...props} />} />
              <Route path={`${match.url}/login-history`} render={props => <LoginHistory {...props} />} />
              {/* <ProtectedRoute
                    path={`${match.url}/second-menu`}
                    component={SecondMenu}
                    roles={[UserRole.Admin]}
            /> */}
              <Route path={`${match.url}/blank-page`} render={props => <BlankPage {...props} />} />
              <Redirect to="/error" />
            </Switch>
          </Suspense>
        )}
      </div>
    </AppLayout>
  );
};

const mapStateToProps = ({ menu }) => {
  const { containerClassnames } = menu;
  return { containerClassnames };
};

export default withRouter(connect(mapStateToProps, {})(App));
