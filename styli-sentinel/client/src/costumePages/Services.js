import React, { useEffect, useState } from 'react';
import { Row, Card, CardBody, CardSubtitle, Button } from 'reactstrap';
import Switch from 'rc-switch';
import { Separator, Colxx } from '../components/common/CustomBootstrap';
import { Link } from 'react-router-dom';
// import axios from 'axios';
import { RESPONSE_STATUS } from '../uitils/responseStatus';
import axiosInstance from '../uitils/axios';
import AddNewServicesModal from './costumComponents/AddNewServicesModal';
import { createNotification } from './costumComponents/Notifications';
import DeleteModal from './costumComponents/DeleteModal';
import Pagination from './costumComponents/Pagination';

export default function HomePage() {
  const [services, setServices] = useState([]);
  const [isChange, setChange] = useState();
  const [pages, setPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [openDeleteModal, setOpenDeleteModal] = useState(false);

  const [selectedService, setSelectedService] = useState({});

  const [openAddModal, setOpenAddModal] = useState(false);
  const [activeEditFields, setActiveEditfields] = useState(false);

  useEffect(() => {
    getServices();
    if (isChange) {
      setChange(false);
    }
  }, [isChange, currentPage]);

  const getServices = async () => {
    try {
      const payload = { pagination: { page: currentPage, pageSize: 9, search: "Terra" } };
      const response = await axiosInstance.post('api/v1/admin/service', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        setServices(response.data.data.services);
        setPages(response.data.data.totalCount)
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  const handleDelete = async () => {
    try {
      console.log('selected service  ===', selectedService);
      const payload = { _id: selectedService['_id'] };
      const response = await axiosInstance.post('api/v1/admin/service/delete', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        createNotification({
          type: 'success',
          title: 'Delete Service',
          subtitle: 'Service Deleted Successfully'
        });
      }
    } catch {
      console.log('err deleting service');
      createNotification({
        type: 'error',
        title: 'Delete Service',
        subtitle: 'Something Went Wrong'
      });
    }
    setSelectedService({});
    setChange(true);
  };

  const handleEdit = async serviceData => {
    setChange(true);
    let payload = serviceData;

    payload = { ...payload, domain: serviceData._id };
    delete payload['_id'];
    delete payload['__v'];
    delete payload['createdAt'];
    delete payload['updatedAt'];

    const response = await axiosInstance.put('api/v1/admin/service/add', payload);
    if (response?.status === 200) {
      console.log('success');
      createNotification({
        type: 'success',
        title: 'Update Service',
        subtitle: 'Service Updated Successfully'
      });
    } else {
      console.log('failed');
      createNotification({
        type: 'danger',
        title: 'Update Service',
        subtitle: 'Something Went Wrong'
      });
    }
    setSelectedService({});
  };

  const setNewServicesData = async newServices => {
    const payload = {
      domain: newServices.domain,
      domainName: newServices.domainName,
      name: newServices.name,
      description: newServices.description,
      authorization: newServices.authorization || false,
      authentication: newServices.authentication || false,
      status: newServices.status || false,
      verifyotp: newServices.verifyotp || false
    };

    const response = await axiosInstance.post('api/v1/admin/service/add', payload);
    if (response?.status === 200) {
      setChange(true);
      createNotification({
        type: 'success',
        title: 'Add Service',
        subtitle: 'Service Added Successfully'
      });
    } else {
      createNotification({
        type: 'danger',
        title: 'Add Service',
        subtitle: 'Something Went Wrong'
      });
      console.log('Failes to Add Service');
    }
    setSelectedService({});
  };

  const onPageChange = (pageNumber) => {
    setCurrentPage(pageNumber)
  }

  return (
    <>
      <Row>
        <Colxx xxs="12">
          <div className="d-flex justifyContent-spaceBtwn alignItm-cen">
            <h1>Services</h1>
            <Button
              onClick={() => {
                setOpenAddModal(true);
              }}
            >
              Add Service
            </Button>
          </div>
        </Colxx>
      </Row>
      <Separator className="mb-5" />
      <Row>
        {services &&
          services.map((item, key) => (
            <Colxx xxs="12" xs="6" lg="4">
              <Card className="mb-4">
                <div className="pl-4 pr-4 pt-4 d-flex">
                  <h2>{item.name}</h2>
                </div>
                <CardBody className="pt-1">
                  <CardSubtitle>{item.description}</CardSubtitle>
                  <div className="m-1">
                    <div className="d-flex align-items-center p-1">
                      Authorization: <span>&nbsp;</span>
                      <div style={item.authorization === true ? { color: 'green' } : { color: 'red' }}>
                        {item.authorization === true ? 'Enabled' : 'Disabled'}
                      </div>
                    </div>
                    <div className="d-flex align-items-center p-1">
                      Authentication:<span>&nbsp;</span>
                      <div style={item.authentication === true ? { color: 'green' } : { color: 'red' }}>
                        {item.authentication === true ? 'Enabled' : 'Disabled'}
                      </div>
                    </div>
                    <div className="d-flex align-items-center p-1">
                      Verify-otp:<span>&nbsp;</span>
                      <div style={item.verifyotp === true ? { color: 'green' } : { color: 'red' }}>
                        {item.verifyotp === true ? 'Enabled' : 'Disabled'}
                      </div>
                    </div>
                    <div className="mt-4 text-right">
                      <Button
                        onClick={() => {
                          setSelectedService(item);
                          setOpenDeleteModal(true);
                        }}
                        className="ml-auto mr-2"
                        color="danger"
                      >
                        Delete
                      </Button>
                      <Button
                        onClick={() => {
                          setSelectedService(item);
                          setActiveEditfields(true);
                        }}
                        outline
                        size="sm"
                        className="mr-2"
                      >
                        Edit
                      </Button>
                      <Link to={`/app/actions/viewAction/${item._id}`}>
                        <Button size="sm">Manage Service</Button>{' '}
                      </Link>
                    </div>
                    {/* </FormGroup> */}
                  </div>
                </CardBody>
              </Card>
            </Colxx>
          ))}
        <Colxx xs="12">
          <div>
            <Pagination
              totalItems={pages}
              currentPage={currentPage}
              onChangePage={e => onPageChange(e)}
              pageSize={9}
              className="m-auto"
            />
          </div>
        </Colxx>
      </Row>
      {(openAddModal || activeEditFields) && (
        <AddNewServicesModal
          openAddModal={openAddModal || activeEditFields}
          setOpenAddModal={activeEditFields ? setActiveEditfields : setOpenAddModal}
          isEdit={activeEditFields}
          setSelectedService={setSelectedService}
          selectedService={selectedService}
          response={resp => {
            resp && activeEditFields ? handleEdit(selectedService) : setNewServicesData(selectedService);
          }}
        />
      )}
      {openDeleteModal && (
        <DeleteModal
          openDeleteModal={openDeleteModal}
          setOpenDeleteModal={setOpenDeleteModal}
          response={resp => {
            resp ? handleDelete() : '';
          }}
        />
      )}
    </>
  );
}
