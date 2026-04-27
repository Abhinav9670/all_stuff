import React, { useEffect, useState } from 'react';
import { DragDropContext, Draggable, Droppable } from 'react-beautiful-dnd';
import { Card, CardBody, Row, Input, Label, FormGroup } from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';
import CostumFormRoles from './costumFormRoles';
import Switch from 'rc-switch';
import axiosInstance from '../../uitils/axios';
import CostumFormUsers from './costumFormUsers';
import { createNotification } from './Notifications';

const onDragEnd = (result, columns, setColumns) => {
  if (!result.destination) return;
  const { source, destination } = result;

  if (source.droppableId !== destination.droppableId) {
    const sourceColumn = columns[source.droppableId];
    const destColumn = columns[destination.droppableId];
    const sourceItems = [...sourceColumn.items];
    const destItems = [...destColumn.items];
    const [removed] = sourceItems.splice(source.index, 1);
    destItems.splice(destination.index, 0, removed);
    setColumns({
      ...columns,
      [source.droppableId]: {
        ...sourceColumn,
        items: sourceItems
      },
      [destination.droppableId]: {
        ...destColumn,
        items: destItems
      }
    });
  } else {
    const column = columns[source.droppableId];
    const copiedItems = [...column.items];
    const [removed] = copiedItems.splice(source.index, 1);
    copiedItems.splice(destination.index, 0, removed);
    setColumns({
      ...columns,
      [source.droppableId]: {
        ...column,
        items: copiedItems
      }
    });
  }
};

export default function DragDropComponent_og({
  roleDetails = {},
  setSelecteditem = () => { },
  type = '',
  endPoints = [],
  selectedService = '',
  setReloadResp = () => { }
}) {
  const [formData, setFormData] = useState({});
  const [columns, setColumns] = useState({});
  const [refreshResp, setRefreshResp] = useState(false);
  const [searchWord, setSearchWord] = useState('');
  const [allEndpoints, setAllEndPoints] = useState([]);

  const handleSearchKeywordChange = value => {
    const { toDo: { items = [] } = {} } = columns;
    if (!allEndpoints.length > 0) {
      setAllEndPoints(items);
    }
    if (value && value.length > 0) {
      const result = allEndpoints.filter(item => {
        let lowercaseItem = item.content.toLowerCase(), lowerCaseSearchValue = value.toLowerCase();
        return lowercaseItem.includes(lowerCaseSearchValue)
      });
      setColumns({ ...columns, toDo: { ...columns.toDo, items: result } });
    } else {
      setColumns({ ...columns, toDo: { ...columns.toDo, items: [...allEndpoints] } });
    }
  };

  useEffect(() => {
    handleSearchKeywordChange(searchWord);
  }, [searchWord]);

  useEffect(() => {
    setFormData(roleDetails);
    let availableDraggableData = [];
    let usedDraggableData = [];

    if (type === 'Role' && roleDetails && roleDetails.action && roleDetails.action.length > 0) {
      roleDetails.action.map((item, index) => {
        usedDraggableData.push({ id: item, content: item });
      });
    }

    if (type === 'Users' && roleDetails && roleDetails.roles && roleDetails.roles.length > 0) {
      roleDetails.roles.map((item, index) => {
        let itemName = endPoints.find(element => element._id === item)?.name;
        usedDraggableData.push({ id: item, content: itemName });
      });
    }

    if (type === 'Role') {
      if (endPoints.length > 0) {
        endPoints.map((item, index) => {
          let tempObj = { id: item._id, content: item._id };
          let count = 0;
          for (let i = 0; i < usedDraggableData.length; i++) {
            if (tempObj.id === usedDraggableData[i].id) {
              count++;
            }
          }
          if (count === 0) {
            availableDraggableData.push(tempObj);
          }
        });
      }
    }
    if (type === 'Users') {
      if (endPoints.length > 0) {
        endPoints.map((item, index) => {
          let tempObj = { id: item._id, content: item.name };
          let count = 0;
          for (let i = 0; i < usedDraggableData.length; i++) {
            if (tempObj.id === usedDraggableData[i].id) {
              count++;
            }
          }
          if (count === 0) {
            availableDraggableData.push(tempObj);
          }
        });
      }
    }
    let taskStatus = {
      requested: {
        name: 'Role',
        items: usedDraggableData
      },
      toDo: {
        name: 'Services',
        items: availableDraggableData
      }
    };
    setColumns(taskStatus);
    setAllEndPoints([]);
  }, [endPoints, roleDetails, refreshResp]);

  useEffect(() => {
    let tempArray = [];
    if (columns.requested && columns.requested.items && columns.requested.items.length > 0) {
      columns.requested.items.map((item, index) => {
        if (type === 'Role') {
          tempArray.push(item.content);
        }
        if (type === 'Users') {
          tempArray.push(item.id);
        }
      });
    }
    if (type === 'Role') {
      setFormData({ ...formData, action: tempArray });
    }
    if (type === 'Users') {
      setFormData({ ...formData, roles: tempArray });
    }
  }, [columns]);


  const handleDelete = async itemId => {
    try {
      let payload = {
        _id: itemId
      };

      if (type === 'Role') {
        const response = await axiosInstance.post('api/v1/admin/roles/delete', payload);
        if (response.status === 200) {
          createNotification({
            type: 'warning',
            title: 'Delete Role',
            subtitle: 'Role Deleted Successfully'
          });
          setReloadResp(true);
        }
      }

      if (type === 'Users') {
        console.log('payload ===', payload);
        const response = await axiosInstance.post('api/v1/admin/user/delete', payload);
        if (response.status === 200) {
          createNotification({
            type: 'warning',
            title: 'Delete User',
            subtitle: 'User Deleted Successfully'
          });
          setReloadResp(true);
        }
      }
    } catch (error) {
      createNotification({
        type: 'error',
        title: 'Something Went Wrong',
        subtitle: ''
      });
      console.error('Error fetching services:', error);
    }
  };

  const handleSubmit = async (formData, requestType) => {
    try {
      let payload = formData;

      console.log('payload====', payload, requestType);

      if (type === 'Role') {
        if (payload._id) {
          delete payload.createdAt;
          delete payload.updatedAt;
          delete payload.__v;

          const response = await axiosInstance.put('api/v1/admin/roles/add', payload);
          if (response.status === 200) {
            createNotification({
              type: 'success',
              title: 'Update Role',
              subtitle: 'Role Updated Successfully'
            });
            setReloadResp(true);
          }
        }
        if (payload._id === undefined || payload._id === null) {
          payload = { ...payload, service: [`${selectedService}`] };
          const response = await axiosInstance.post('api/v1/admin/roles/add', payload);
          if (response.data.code === 200) {
            createNotification({
              type: 'success',
              title: 'Create Role',
              subtitle: 'Role Created Successfully'
            });
            setReloadResp(true);
          }
        }
      }
      if (type === 'Users') {
        if (requestType === 'post') {
          const response = await axiosInstance.post('api/v1/admin/user/add', payload);
          if (response.data.code === 200) {
            createNotification({
              type: 'success',
              title: 'Create User',
              subtitle: 'User Created Successfully'
            });
            setReloadResp(true);
          }
          if (response.data.code === 409) {
            createNotification({
              type: 'error',
              title: 'Create User',
              subtitle: response.data?.message
            });
            setReloadResp(true);
          }
          setTimeout(() => {
            window.location.reload(false);
          }, 2000);
        }
        if (requestType === 'put') {
          delete payload.createdAt;
          delete payload.updatedAt;
          delete payload.__v;
          delete payload.last_logged_in_time;
          delete payload.login_status;
          delete payload.uuid;
          delete payload.userType;

          const response = await axiosInstance.put('api/v1/admin/user/add', payload);
          if (response.data.code === 200) {
            createNotification({
              type: 'success',
              title: 'Update User',
              subtitle: 'User Updated Successfully'
            });
            setReloadResp(true);
          }
        }
      }
    } catch (error) {
      console.error('Error fetching services:', error);
    }
  };

  return (
    <div>
      <Row>
        <DragDropContext onDragEnd={result => onDragEnd(result, columns, setColumns)}>
          {Object.entries(columns).map(([columnId, column], index) => {
            return (
              <Colxx xxs="6" key={columnId} className={`${index === 0 ? 'card_layout' : ''}`}>
                <div className={index === 0 ? 'pt-4' : ''}>
                  <div className={`${index === 1 ? 'pl-2' : 'pl-4 pr-4'} justifyContent-cen alignItm-cen`}>
                    <Row>
                      <Colxx xxs="8">
                        {index === 0 ? (
                          <h2>{roleDetails.name || `Create ${type}`}</h2>
                        ) : (
                          <h2>{type == 'Users' ? 'Available Roles' : 'End Points'}</h2>
                        )}
                      </Colxx>
                      {/* <Colxx xxs="4">
                          {index === 0 && (
                            <div>
                              <FormGroup className="d-flex p-2">
                                <Label className="pr-2" for="exCustomCheckbox">
                                  Status
                                </Label>
                                <Switch
                                  className="custom-switch custom-switch-primary custom-switch-small"
                                  checked={roleDetails.status}
                                  onChange={primary => {}}
                                />
                              </FormGroup>
                            </div>
                          )}
                        </Colxx> */}
                    </Row>
                  </div>
                  <div
                    style={index === 1 ? { height: '500px', overflowX: 'hidden' } : {}}
                    className={index === 0 ? 'pr-4 pl-4' : ''}
                  >
                    {index === 0 && <Label>{type === 'Roles' ? 'Actions' : 'Roles'}</Label>}
                    {index === 1 && (
                      <div className="search alignItm-cen mr-2 ml-2 mb-3 mt-2">
                        <Input
                          name="searchKeyword"
                          id="searchKeyword"
                          placeholder="Search"
                          value={searchWord}
                          onChange={e => setSearchWord(e.target.value)}
                          style={{ borderRadius: '5px' }}
                        />
                      </div>
                    )}
                    <Droppable droppableId={columnId} key={columnId}>
                      {(provided, snapshot) => {
                        return (
                          <div
                            {...provided.droppableProps}
                            ref={provided.innerRef}
                            style={{
                              border: `${index === 0 ? '1px solid lightgray' : ''}`,
                              minHeight: '100px',
                              maxHeight: `${index === 0 ? '200px' : '500px'}`,
                              overflowX: 'hidden'
                            }}
                          >
                            {index === 0 && column.items.length === 0 && (
                              <div className="text-center pt-4">Drag Roles hear to add.</div>
                            )}
                            {column.items.map((item, index) => {
                              return (
                                <Draggable key={item.id} draggableId={item.id} index={index}>
                                  {(provided, snapshot) => {
                                    return (
                                      <div
                                        ref={provided.innerRef}
                                        {...provided.draggableProps}
                                        {...provided.dragHandleProps}
                                        style={{
                                          userSelect: 'none',
                                          padding: 16,
                                          margin: '10px',
                                          minHeight: '50px',
                                          backgroundColor: 'white',
                                          borderRadius: '10px',
                                          wordBreak: 'break-all',
                                          ...provided.draggableProps.style,
                                          boxShadow: '0 1px 15px rgba(0, 0, 0, 0.04), 0 1px 6px rgba(0, 0, 0, 0.04)'
                                        }}
                                        className="text-muted"
                                      >
                                        {item.content}
                                      </div>
                                    );
                                  }}
                                </Draggable>
                              );
                            })}
                          </div>
                        );
                      }}
                    </Droppable>
                  </div>
                  <div className="p-4 justifyContent-cen alignItm-cen">
                    {index === 0 && (
                      <div>
                        {type === 'Role' && (
                          <CostumFormRoles
                            className="pl-4 pr-4"
                            editData={formData}
                            setData={setSelecteditem}
                            handleSubmit={handleSubmit}
                            handleDelete={handleDelete}
                            setRefreshResp={setRefreshResp}
                          />
                        )}
                        {type === 'Users' && (
                          <CostumFormUsers
                            className="pl-4 pr-4"
                            editData={formData}
                            setData={setSelecteditem}
                            handleSubmit={handleSubmit}
                            handleDelete={handleDelete}
                            setRefreshResp={setRefreshResp}
                          />
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </Colxx>
            );
          })}
        </DragDropContext>
      </Row>
    </div>
  );
}
