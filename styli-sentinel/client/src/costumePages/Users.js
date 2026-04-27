import React, { useEffect, useState } from 'react';
import { Separator } from '../components/common/CustomBootstrap';
import { Row, Input } from 'reactstrap';
import { Colxx } from '../components/common/CustomBootstrap';
import Table from './costumComponents/costumReactTableCards';
import DragDropComponent_og from './costumComponents/DragDropComponent_og';
import axiosInstance from '../uitils/axios';
import { RESPONSE_STATUS } from '../uitils/responseStatus';
import { createNotification } from './costumComponents/Notifications';
import UserSearch from './costumComponents/UserSearch';

export default function Users() {
  const tableHeaderData = [
    {
      Header: 'Users',
      accessor: 'name',
      cellClass: 'text-muted w-20',
      Cell: props => <>{props.value}</>
    }
  ];

  const tableBodyData = [
    {
      id: 1,
      name: 'User 1',
      service: [],
      action: [],
      status: false
    }
  ];

  const [selectedUser, setSelectedUser] = useState({});

  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [reloadResp, setReloadResp] = useState(false);
  const [allUsers, setAllUsers] = useState([]);

  const getUsers = async () => {
    try {
      const payload = { pagination: { page: 1, pageSize: 5000 } };
      const response = await axiosInstance.post('api/v1/admin/users', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        setUsers(response?.data?.data?.user);
        setAllUsers(response?.data?.data?.user);
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  const getRoles = async () => {
    try {
      const payload = { pagination: { page: 1, pageSize: 5000 } };
      const response = await axiosInstance.post('api/v1/admin/roles', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        setRoles(response.data.data.role);
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  useEffect(() => {
    getUsers();
    if (reloadResp === true) {
      setReloadResp(false);
    }
  }, [reloadResp]);

  useEffect(() => {
    getUsers();
    getRoles();
  }, []);

  const handleUserSearch = (value) => {
    if (value.length > 0) {
      // if (allUsers.length === 0) {
      //   setAllUsers(allUsers);
      // }
      const result = allUsers.filter(user =>
        user.name.toLowerCase().includes(value.toLowerCase())
      );
      setUsers(result);
    } else {
      if (allUsers.length > 0) {
        setUsers(allUsers);
        // setAllUsers(allUsers);
      }
    }
  };


  return (
    <div>
      <Row>
        <Colxx xxs="12">
          <h1>Users</h1>
          <Separator />
        </Colxx>
      </Row>
      <Row className="mt-4">
        <Colxx xxs="4">
          <Row>
            <Colxx>
              <div className="alignItm-cen mb-2">
                <UserSearch handleUserSearch={handleUserSearch} />
              </div>
            </Colxx>
          </Row>
          <Table
            columns={tableHeaderData}
            data={users}
            defaultPageSize={500}
            setSelecteditem={setSelectedUser}
            divided
            hideHeader={true}
          />
        </Colxx>
        <Colxx xxs="8">
          <DragDropComponent_og
            type="Users"
            roleDetails={selectedUser}
            setSelecteditem={setSelectedUser}
            endPoints={roles}
            setReloadResp={setReloadResp}
          />
        </Colxx>
      </Row>
    </div>
  );
}
