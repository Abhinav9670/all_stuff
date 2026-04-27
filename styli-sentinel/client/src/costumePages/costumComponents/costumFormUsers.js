import React, { useEffect, useState } from 'react';
import { FormGroup, Label, CustomInput, Form, Input, Row, Button } from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';
import Switch from 'rc-switch';
import DeleteModal from './DeleteModal';

export default function CostumFormRoles({
  editData = {},
  setData = () => { },
  response = () => { },
  handleSubmit = () => { },
  handleDelete = () => { },
  setRefreshResp = () => { }
}) {
  const [fieldValues, setFieldvalues] = useState({});
  const [requestType, setRequestType] = useState('post');
  const [openDeleteModal, setOpenDeleteModal] = useState(false);

  const deleteHandler = () => {
    handleDelete(editData._id);
    setFieldvalues({});
    setData({});
    setRefreshResp(true);
  };

  useEffect(() => {
    setRequestType(Object.keys(editData).length > 1 ? 'put' : 'post')
    setFieldvalues((fieldValues) => ({ ...fieldValues, ...editData }))
  }, [editData]);

  return (
    <div>
      <Form>
        <FormGroup>
          <Label for="exCustomCheckbox">Name</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              onChange={e => {
                setFieldvalues({ ...fieldValues, name: e.target.value });
              }}
              value={fieldValues.name || ''}
            />
          </div>
        </FormGroup>
        <FormGroup>
          <Label for="exCustomCheckbox">Email</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              onChange={e => {
                setFieldvalues({ ...fieldValues, _id: e.target.value });
              }}
              value={fieldValues._id || ''}
            />
          </div>
        </FormGroup>
        <Row>
          <Colxx xxs="6">
            <FormGroup className="p-2">
              <Label className="pr-2" for="exCustomCheckbox">
                Authorization
              </Label>
              <Switch
                className="custom-switch custom-switch-primary custom-switch-small"
                checked={fieldValues.authorization || false}
                onChange={e => {
                  setFieldvalues({ ...fieldValues, authorization: e });
                }}
              />
            </FormGroup>
          </Colxx>
          <Colxx xxs="6">
            <FormGroup className="p-2">
              <Label className="pr-2" for="exCustomCheckbox">
                Authentication
              </Label>
              <Switch
                className="custom-switch custom-switch-primary custom-switch-small"
                checked={fieldValues.authentication || false}
                onChange={e => {
                  setFieldvalues({ ...fieldValues, authentication: e });
                }}
              />
            </FormGroup>
          </Colxx>
        </Row>
        <Row>
          <Colxx xxs="5" className="text-left">
            {requestType === 'put' && (
              <Button
                outline
                onClick={() => {
                  setFieldvalues({});
                  setData({});
                  setRefreshResp(true);
                }}
              >
                Add New
              </Button>
            )}
          </Colxx>
          <Colxx xxs="7" className="text-right d-flex p-1">
            <Button onClick={() => setOpenDeleteModal(true)} className="mr-1" color="danger">
              Delete
            </Button>{' '}
            <Button
              onClick={() => {
                handleSubmit(fieldValues, requestType);
              }}
            >
              {requestType === 'put' ? 'Update' : 'Create'}
            </Button>
          </Colxx>
        </Row>
      </Form>
      <DeleteModal
        openDeleteModal={openDeleteModal}
        setOpenDeleteModal={setOpenDeleteModal}
        response={resp => (resp ? deleteHandler() : null)}
      />
    </div>
  );
}
