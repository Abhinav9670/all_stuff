import React, { Suspense } from 'react';
import { Redirect, Route, Switch } from 'react-router-dom';

const Actions = React.lazy(() => import(/* webpackChunkName: "second" */ './ActionsPage'));
const CostumePages = ({ match }) => (
  <Suspense fallback={<div className="loading" />}>
    <Switch>
      <Redirect exact from={`${match.url}/`} to={`${match.url}/second`} />
      <Route path={`${match.url}/actions`} render={props => <Actions {...props} />} />
      <Route path={`${match.url}/viewAction/:id`} render={props => <Actions data={props} />} />
      <Redirect to="/error" />
    </Switch>
  </Suspense>
);
export default CostumePages;
