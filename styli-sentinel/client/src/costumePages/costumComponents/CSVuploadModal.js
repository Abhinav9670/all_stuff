import React, { useEffect, useState } from 'react';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import CSVdragNdrop from './CSVdragNdrop';
import { RESPONSE_STATUS } from '../../uitils/responseStatus';
import axiosInstance from '../../uitils/axios';
import { createNotification } from './Notifications';

export default function CSVuploadModal({
  openUploadModal = 'false',
  setOpenUploadModal = () => {},
  setReloadResp = () => {}
}) {
  const [csvJsonData, setcsvJsonData] = useState([]);

  const getCsvData = csvData => {
    setcsvJsonData(csvData);
    console.log(csvData);
  };

  const bulkUpload = async () => {
    try {
      const payload = csvJsonData.data;
      const response = await axiosInstance.post('api/v1/admin/bulk-upload', payload);
      if (response && response.data && response.status === RESPONSE_STATUS.success) {
        if (response && response.data && response.status === RESPONSE_STATUS.success) {
          setReloadResp(true);
          createNotification({
            type: 'success',
            title: 'Bulk Upload',
            subtitle: 'CSV Uploaded Successfully'
          });
        }
      }
    } catch (error) {
      console.log('reaching here for error');
      createNotification({
        type: 'error',
        title: 'Bulk Upload',
        subtitle: 'Error While Uploading CSV'
      });
    }
  };

  return (
    <Modal size="lg" isOpen={openUploadModal}>
      <ModalHeader>Upload CSV</ModalHeader>
      <ModalBody>
        <CSVdragNdrop getCsvData={getCsvData} />
      </ModalBody>
      <ModalFooter>
        <Button
          outline
          onKeyDown={() => setFiles([])}
          style={{ float: 'right' }}
          className="c-pointer"
          color="danger"
          onClick={() => setOpenUploadModal(false)}
        >
          Cancel
        </Button>
        <Button
          onClick={() => {
            bulkUpload();
            setOpenUploadModal(false);
          }}
        >
          Upload
        </Button>
      </ModalFooter>
    </Modal>
  );
}
