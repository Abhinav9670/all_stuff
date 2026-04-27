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
  Row
} from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';
import Switch from 'rc-switch';

export default function AddNewServicesModal({
  openAddModal = false,
  setOpenAddModal = () => {},
  response = () => {},
  selectedService = {},
  setSelectedService = () => {},
  isEdit = false
}) {
  return (
    <div>
      <Modal toggle={openAddModal} isOpen={openAddModal}>
        <ModalHeader>Add Service</ModalHeader>
        <ModalBody>
          <Form>
            {!isEdit && (
              <FormGroup>
                <Label for="exCustomCheckbox">Domain</Label>
                <div>
                  <Input
                    type="text"
                    id="exCustomCheckbox"
                    label="Check this custom checkbox"
                    onChange={e =>
                      setSelectedService({
                        ...selectedService,
                        domain: e.target.value
                      })
                    }
                    value={selectedService.domain}
                  />
                </div>
              </FormGroup>
            )}
            <FormGroup>
              <Label for="exCustomCheckbox">Name</Label>
              <div>
                <Input
                  type="text"
                  id="exCustomCheckbox"
                  label="Check this custom checkbox"
                  onChange={e =>
                    setSelectedService({
                      ...selectedService,
                      name: e.target.value
                    })
                  }
                  value={selectedService.name}
                />
              </div>
            </FormGroup>
            <FormGroup>
              <Label for="exCustomCheckbox">Description</Label>
              <div>
                <Input
                  type="textarea"
                  id="exCustomCheckbox"
                  label="Check this custom checkbox"
                  value={selectedService.description}
                  onChange={e =>
                    setSelectedService({
                      ...selectedService,
                      description: e.target.value
                    })
                  }
                />
              </div>
            </FormGroup>
            <Row>
              <Colxx xxs="6">
                <FormGroup>
                  <div>
                    <CustomInput
                      type="checkbox"
                      id="exCustomCheckbox"
                      label="Authorization"
                      onChange={e =>
                        setSelectedService({
                          ...selectedService,
                          authorization: e.target.checked
                        })
                      }
                      checked={selectedService.authorization}
                    />
                    <CustomInput
                      type="checkbox"
                      id="exCustomCheckbox2"
                      label="Authentication"
                      onChange={e =>
                        setSelectedService({
                          ...selectedService,
                          authentication: e.target.checked
                        })
                      }
                      checked={selectedService.authentication}
                    />
                    <CustomInput
                      type="checkbox"
                      id="exCustomCheckbox"
                      label="Verify-otp"
                      onChange={e =>
                        setSelectedService({
                          ...selectedService,
                          verifyotp: e.target.checked
                        })
                      }
                      checked={selectedService.verifyotp}
                    />
                  </div>
                </FormGroup>
              </Colxx>
              <Colxx xxs="6">
                <FormGroup>
                  <Label for="exCustomCheckbox">Status</Label>
                  <Switch
                    className="custom-switch custom-switch-primary custom-switch-small"
                    checked={selectedService.status}
                    onChange={e => setSelectedService({ ...selectedService, status: e })}
                  />
                </FormGroup>
              </Colxx>
            </Row>
          </Form>
        </ModalBody>
        <ModalFooter>
          <Button
            onClick={() => {
              setOpenAddModal(false);
              setSelectedService({});
            }}
          >
            Close
          </Button>
          <Button
            onClick={() => {
              response(true);
              setOpenAddModal(false);
            }}
          >
            {isEdit ? 'Update' : 'Add'}
          </Button>
        </ModalFooter>
      </Modal>
    </div>
  );
}
