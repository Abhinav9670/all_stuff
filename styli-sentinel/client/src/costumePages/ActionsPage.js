import React, { useCallback, useEffect, useState } from 'react';
import { Button, Input, Row } from 'reactstrap';
import { Colxx, Separator } from '../components/common/CustomBootstrap';
import Table from './costumComponents/costumReactTableCards';
import EditCard from './costumComponents/EditCard';
import { RESPONSE_STATUS } from '../uitils/responseStatus';
import axiosInstance from '../uitils/axios';
import CSVuploadModal from './costumComponents/CSVuploadModal';
import { createNotification } from './costumComponents/Notifications';
import { debounce } from 'lodash';

export default function Actions(props) {
  const tableHeaderData = [
    {
      Header: 'Name',
      accessor: 'name',
      cellClass: 'text-muted w-20',
      Cell: props => <>{props.value}</>
    },
    {
      Header: 'Domain',
      accessor: 'domain',
      cellClass: 'text-muted w-10',
      Cell: props => <>{props.value}</>
    },
    {
      Header: 'Url',
      accessor: 'url',
      cellClass: 'text-muted w-35',
      Cell: props => <>{props.value}</>
    },
    {
      Header: 'HTTP Method',
      accessor: 'httpMethod',
      cellClass: 'text-muted w-5',
      Cell: props => <>{props.value}</>
    }
  ];
  const [tableBodyData, setTableBodyData] = useState([]);
  const [totalItems, setTotalItems] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [reloadResp, setReloadResp] = useState(false);
  const [selectedItem, setSelecteditem] = useState({});
  const [search, setSearch] = useState("");

  const [formType, setFormType] = useState(selectedItem._id ? 'save' : 'create');

  useEffect(() => {
    if (selectedItem._id) {
      setFormType('save');
    } else {
      setFormType('create');
    }
  }, [selectedItem]);

  useEffect(() => {
    debouncedGetAction(search, currentPage);
  }, [search, currentPage]);

  const debouncedGetAction = useCallback(
    debounce(async (searchTerm, page) => {
      try {
        const payload = {
          filter: { domain: props?.data?.match?.params?.id, url: searchTerm || undefined },
          pagination: { page, pageSize: 10 },
        };

        const response = await axiosInstance.post('api/v1/admin/action', payload);
        if (response && response.data && response.status === RESPONSE_STATUS.success) {
          setTableBodyData(response.data.data.actions);
          setTotalItems(response.data.data.totalCount);
        }
      } catch (error) {
        console.error('Error fetching services:', error);
      }
    }, 1000),
    []
  );
  const [openUploadModal, setOpenUploadModal] = useState(false);

  const getResponse = async resp => {
    if (resp === 'create') {
      console.log('create', selectedItem);
      try {
        let payload = selectedItem;
        payload = {
          ...payload,
          authorization: selectedItem.authorization || false,
          authentication: selectedItem.authentication || false,
          status: selectedItem.status || false,
          domain: props?.data?.match?.params?.id
        };
        delete payload._id;
        delete payload.createdAt;
        delete payload.updatedAt;
        delete payload.__v;

        const response = await axiosInstance.post('api/v1/admin/action/add', payload);
        if (response && response.data && response.status === RESPONSE_STATUS.success) {
          console.log('data' + response.data.data.actions);
          setTableBodyData(response.data.data.actions);
        }
      } catch (error) {
        console.error('Error fetching services:', error);
      }
    } else if (resp === 'save') {
      console.log('save', selectedItem);
      try {
        let payload = selectedItem;
        payload = {
          ...payload,
          authorization: selectedItem.authorization || false,
          authentication: selectedItem.authentication || false,
          status: selectedItem.status || false,
          domain: props?.data?.match?.params?.id
        };
        delete payload._id;
        delete payload.createdAt;
        delete payload.updatedAt;
        delete payload.__v;

        const response = await axiosInstance.put('api/v1/admin/action/add', payload);
        if (response && response.data && response.status === RESPONSE_STATUS.success) {
          console.log('data' + response.data.data.actions);
          setTableBodyData(response.data.data.actions);
        }
      } catch (error) {
        console.error('Error fetching services:', error);
      }
    }
    getAction();
  };

  const handleDelete = async () => {
    try {
      const payload = {
        _id: selectedItem._id
      };

      const response = await axiosInstance.post('api/v1/admin/action/delete', payload);
      if (response.status === 200) {
        getAction();
        createNotification({
          type: 'warning',
          title: 'Delete Role',
          subtitle: 'Role Deleted Successfully'
        });
      }
    } catch {
      createNotification({
        type: 'error',
        title: 'Something Went Wrong',
        subtitle: ''
      });
      console.error('Error fetching services:', error);
    }
  };

  useEffect(() => {
    console.log('getting here');
    if (reloadResp === true) {
      getAction();
      setReloadResp(false);
    }
  }, [reloadResp]);

  const {
    data: {
      match: {
        params: { id }
      }
    }
  } = props;

  return (
    <>
      <Row>
        <Colxx xxs="10">
          <h1>{id}</h1>
        </Colxx>
        <Colxx xxs="2">
          <div className="ml-auto blkUpload-btn">
            <Button onClick={() => setOpenUploadModal(true)}>Bulk Upload</Button>
          </div>
        </Colxx>
      </Row>
      <Separator />
      <Row className="pt-4">
        <Colxx xxs="8">
          <div className='mb-2'>
            <Input
              type="text"
              placeholder="Search"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              onChange={e => {
                setSearch(e.target.value);
                setCurrentPage(1);
              }}
              value={search || ''}
            />
          </div>
        </Colxx>
        <Colxx xxs="8">
          <Table
            columns={tableHeaderData}
            data={tableBodyData}
            setSelecteditem={setSelecteditem}
            divided
            currentPage={currentPage}
            pages={totalItems}
            onPageChange={setCurrentPage}
          />
        </Colxx>
        <Colxx xxs="4">
          {formType && (
            <EditCard
              getResponse={getResponse}
              data={selectedItem}
              setData={setSelecteditem}
              formType={formType}
              setFormType={setFormType}
              handleDelete={handleDelete}
              domain={props?.data?.match?.params?.id}
            />
          )}
        </Colxx>
      </Row>
      {openUploadModal && (
        <CSVuploadModal
          setReloadResp={setReloadResp}
          openUploadModal={openUploadModal}
          setOpenUploadModal={setOpenUploadModal}
        />
      )}
    </>
  );
}
