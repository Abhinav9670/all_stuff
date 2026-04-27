import axios from 'axios';
import { createNotification } from '../costumePages/costumComponents/Notifications';
import { getCurrentUser, removeFromCookie } from '../helpers/Utils';

const axiosOptions = {
  timeout: 500000,
  headers: {},
  baseURL: process.env.SNTNL_API_HOST || 'http://localhost:8000/'
};
const axiosInstance = axios.create(axiosOptions);

axiosInstance.interceptors.request.use(
  async config => {
    const user = getCurrentUser() || null;
    let token;
    if (user) {
      token = user.token;
    }
    config.headers['Authorization'] = `Bearer ${token}`;
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

axiosInstance.interceptors.response.use(
  res => {
    return res;
  },
  error => {
    console.log('error::', error?.response);
    if (error?.response?.status === 403) {
      const msg = 'You do not have adequate permission to access this Page. Please contact your administrator.';
      createNotification({
        type: 'error',
        title: 'Access denied',
        subtitle: msg
      });
      return Promise.reject(error);
    } else {
      if (error?.response?.status === 401) {
        createNotification({
          type: 'error',
          title: 'Authentication failed',
          subtitle: 'Please login Again'
        });
        removeFromCookie();
        window.location.href = "/user/login"
        return Promise.reject(error);
      }
      if (error?.response?.status === 400) {
        createNotification({
          type: 'error',
          title: 'Invalid Request',
          subtitle: "Please fill the form correctly!"
        });
        return Promise.reject(error);
      }
      createNotification({
        type: 'error',
        title: 'Error',
        subtitle: "Something Went Wrong"
      });
      return Promise.reject(error);
    }
  }
);

export default axiosInstance;
