import React, { useCallback } from 'react'
import axiosInstance from '../uitils/axios';
import { RESPONSE_STATUS } from '../uitils/responseStatus';
import moment from 'moment-timezone';
import { createNotification } from '../costumePages/costumComponents/Notifications';
import { useHistory } from 'react-router-dom';

    const getISTTime = (utcTime) => {

        const utcMoment = moment.utc(utcTime);
        const istMoment = utcMoment.tz('Utc');
        return istMoment.format('D/M/YY,hh:mm A');
    };

  export const getUsers = async () => {
        try {
            const payload = { pagination: { page: 1, pageSize: 200 } };
            const response = await axiosInstance.post('api/v1/admin/users', payload);
            if (response && response.data && response.status === RESPONSE_STATUS.success) {
                return response?.data?.data?.user;
            }
        } catch (error) {
            console.error('Error fetching services:', error);
        }
    };

    
   export const tableHeaderData = [
    
        {
            Header: 'Users',
            accessor: 'name',
            cellClass: 'text-muted w-20',
            Cell: props => <>{props.value}</>
        },
        {
            Header: 'UUID',
            accessor: 'uuid',
            cellClass: 'text-muted w-20',
            Cell: props => <>{props.value}</>
        },
        {
            Header: 'Last Logged In Time',
            accessor: 'last_logged_in_time',
            cellClass: 'text-muted w-20',
            Cell: props => <>{props.value ? getISTTime(props.value) : ""}</>
        },
        {
            Header: 'Login Status',
            accessor: 'login_status',
            cellClass: 'text-muted w-20',
            Cell: props => <>{props.value ? <i className="online" /> : <i className="offline" />}</>
        },
        {
            Header: 'Actions',
            cellClass: 'text-muted w-20',
            accessor: row => {
                return (
                    <>
                        <button type="button" class="mr-1 btn btn-danger" onClick={
                            async() => {
                            const usersArray = [row]
                            const response = await axiosInstance.post(`api/v1/auth/force-logout/select/bulk`, { usersArray });
                            if (response && response.data && response.status === RESPONSE_STATUS.success) {
                                createNotification({
                                    type: 'success',
                                    title: 'Forced Logout User',
                                    subtitle: 'Single User Forced Logout'
                                  });
                                  await getUsers();
                                    window.location.href = "/app/login-history"
                            }
                            else if(response.status === 201){
                                createNotification({
                                    type: 'error',
                                    title: 'Force Logout Failed',
                                    subtitle: "Force Logout not enabled"
                                  });
                              }
                        }}
                        >{"Force Logout"}</button>
                    </>
                );
            },
        }
    ];