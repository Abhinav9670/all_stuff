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
  const [openDeleteModal, setOpenDeleteModal] = useState(false);

  useEffect(() => {
    setFieldvalues((fieldValues) => ({ ...fieldValues, ...editData }))
  }, [editData]);

  const deleteHandler = () => {
    handleDelete(editData._id);
    setFieldvalues({});
    setData({});
    setRefreshResp(true);
  };

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
          <Label for="exCustomCheckbox">Description</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={fieldValues.description || ''}
              onChange={e => {
                setFieldvalues({ ...fieldValues, description: e.target.value });
              }}
            />
          </div>
          <Row className="pt-3">
            <Colxx xxs="5" className="text-left">
              {fieldValues._id && (
                <Button
                  onClick={() => {
                    setFieldvalues({});
                    setData({});
                    setRefreshResp(true);
                  }}
                  outline
                >
                  Add New
                </Button>
              )}
            </Colxx>
            <Colxx xxs="7" className="text-right d-flex p-1">
              <Button
                onClick={() => {
                  setOpenDeleteModal(true);
                }}
                className="mr-1"
                color="danger"
              >
                Delete
              </Button>
              <Button
                onClick={() => {
                  handleSubmit(fieldValues);
                }}
              >
                {fieldValues && fieldValues._id ? 'Update' : 'Create'}
              </Button>
            </Colxx>
          </Row>
        </FormGroup>
      </Form>
      {openDeleteModal && (
        <DeleteModal
          openDeleteModal={openDeleteModal}
          setOpenDeleteModal={setOpenDeleteModal}
          response={resp => (resp ? deleteHandler() : null)}
        />
      )}
    </div>
  );
}
