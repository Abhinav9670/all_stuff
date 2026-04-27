import React, { useEffect, useState } from 'react';
import { Row, FormGroup, Label, Input } from 'reactstrap';
import { Separator } from '../components/common/CustomBootstrap';
import { Colxx } from '../components/common/CustomBootstrap';
import Table from './costumComponents/costumReactTableCards';
import DragDropComponent_og from './costumComponents/DragDropComponent_og';
import { RESPONSE_STATUS } from '../uitils/responseStatus';
import axiosInstance from '../uitils/axios';

export default function Roles() {
  const tableHeaderData = [
    {
      Header: 'Role',
      accessor: 'name',
      cellClass: 'text-muted w-20',
      Cell: props => <>{props.value}</>
    }
  ];

  const [selectedItem, setSelecteditem] = useState({});
  const [reloadResp, setReloadResp] = useState(false);

  const [services, setServices] = useState({});

  const [selectedService, setSelectedService] = useState(null);

  const [roles, setRoles] = useState([]);
  const [endPoints, setEndPoints] = useState([]);

  const getServices = async () => {
    try {
      const payload = { pagination: { page: 1, pageSize: 100 } };
      const response = await axiosInstance.post('api/v1/admin/service', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        setServices(response.data.data.services);
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  const getRoles = async () => {
    try {
      let payload = {
        filter: { service: selectedService },
        pagination: {
          page: 1,
          pageSize: 1000
        }
      };

      const response = await axiosInstance.post('api/v1/admin/roles', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        setRoles(response.data.data.role);
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  const getEndPoints = async () => {
    try {
      let payload = {
        filter: { domain: selectedService },
        pagination: {
          page: 1,
          pageSize: 1000
        }
      };

      const response = await axiosInstance.post('api/v1/admin/action', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        setEndPoints(response.data.data.actions);
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  useEffect(() => {
    getServices();
  }, []);

  useEffect(() => {
    if (reloadResp === true) {
      getRoles();
      getEndPoints();
      setReloadResp(false);
    }
  }, [reloadResp]);

  useEffect(() => {
    if (selectedService !== null) {
      getRoles();
      getEndPoints();
    }
  }, [selectedService]);

  useEffect(() => {
    if (selectedService === null && services.length > 0) {
      setSelectedService(services[0]._id);
    }
  }, [services]);

  return (
    <>
      <div className="d-flex">
        <h1>Roles</h1>
        {services && services.length && selectedService && (
          <FormGroup className="ml-auto mr-0">
            <Input
              type="select"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={selectedService}
              style={{ width: '250px' }}
              onChange={e => {
                setSelectedService(e.target.value);
              }}
            >
              {services.map((item, index) => {
                return <option value={item._id}>{item.name}</option>;
              })}
            </Input>
          </FormGroup>
        )}
      </div>
      <Separator />
      <Row className="mt-4">
        <Colxx xxs="4">
          <Table
            columns={tableHeaderData}
            data={roles}
            setSelecteditem={setSelecteditem}
            defaultPageSize={100}
            divided
            hideHeader={true}
          />
        </Colxx>

        <Colxx xxs="8">
          {services && services.length && selectedService && (
            <DragDropComponent_og
              endPoints={endPoints}
              type="Role"
              roleDetails={selectedItem}
              setSelecteditem={setSelecteditem}
              selectedService={selectedService}
              setReloadResp={setReloadResp}
            />
          )}
        </Colxx>
      </Row>
    </>
  );
}
