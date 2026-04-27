import React from 'react';
import { Modal, ModalBody, ModalFooter, ModalHeader, Button } from 'reactstrap';

export default function DeleteModal({
  openDeleteModal = false,
  setOpenDeleteModal = () => {},
  response = () => {},
}) {
  return (
    <div>
      <Modal toggle={openDeleteModal} isOpen={openDeleteModal}>
        <ModalHeader>Delete?</ModalHeader>
        <ModalBody>Are you sure you want to delete this Item?</ModalBody>
        <ModalFooter>
          <Button onClick={() => setOpenDeleteModal(false)}>CANCEL</Button>
          <Button
            color="danger"
            onClick={() => {
              response(true);
              setOpenDeleteModal(false);
            }}
          >
            Confirm
          </Button>
        </ModalFooter>
      </Modal>
    </div>
  );
}
