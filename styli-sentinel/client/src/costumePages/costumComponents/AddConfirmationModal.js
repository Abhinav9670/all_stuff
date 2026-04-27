import React, { useState } from 'react';
import {
  Button,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Form,
  FormGroup,
  Input,
  Label,
  CustomInput,
  Row,
} from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';
import Switch from 'rc-switch';

export default function AddConfirmationModal({
  openConfirmModel = false,
  setOpenConfirmModel = () => { },
  changeState,
  handleEdit,
}) {


  return (
    <div>
      <Modal toggle={openConfirmModel} isOpen={openConfirmModel}>
        <ModalHeader>Status Change</ModalHeader>
        <ModalBody>
          <Form>
            <FormGroup>
              <Label for="exCustomCheckbox">Are you sure you want to perform this edit? </Label>
            </FormGroup>
          </Form>
        </ModalBody>
        <ModalFooter>
          <Button onClick={() => setOpenConfirmModel(false)}>CANCEL</Button>
          <Button
            onClick={() => {
              handleEdit(changeState._id, changeState.column);
              setOpenConfirmModel(false);
            }}
          >
            OK
          </Button>
        </ModalFooter>
      </Modal>
    </div>
  );
}
