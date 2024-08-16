---Carter's Delay
-- version 0.0.1 @williamhazard
-- with thanks to @jaseknighter
--
-- 

engine.name='CartersDelay'
------------------------------
-- init
------------------------------
function init()
  inpass = true
  delin = true
  delout = true
  add_parameters()
  redrawtimer = metro.init(function() 
    redraw()
  end, 1/15, -1)
  print("start redraw")
  redrawtimer:start()  
end

--------------------------
-- encoders and keys
--------------------------
function enc(n, d)
  if n == 1 then
    params:delta("input_passthrough",d)
  elseif n == 2 then
    params:delta("delay_input",d)
  else
    params:delta("input_passthrough",d)
    params:delta("delay_input",d)
  end
end

function key(n,z)
  if z == 1 then
    if n == 1 then
      if inpass then
        inpass = false
        params:set("monitor_level",-inf)
      else
        inpass = true
        params:set("monitor_level",remember)
      end
    end
    if n == 2 then
      if delin then
        delin = false
        params:set("delay_input_onoff",1)
      else
        delin = true
        params:set("delay_input_onoff",2)
      end
    end
    if n == 3 then
      if delout then
        delout = false
        params:set("delay_output_onoff",1)
      else
        delout = true
        params:set("delay_output_onoff",2)
      end
    end
  end
end

--------------------------
-- init params
--------------------------
function add_parameters()
  pre_init_monitor_level = params:get('monitor_level')
  onoff = {"off", "on"}
  remember = pre_init_monitor_level
  params:add_option("input_passthrough_onoff", "input passthru", onoff, 2)
  params:set_action("input_passthrough_onoff",function(value)
      if value == 1 then
        inpass = false
        params:set('monitor_level',-inf)
      else
        inpass = true
        params:set('monitor_level',remember)
      end
  end
  )
  params:add_option("delay_input_onoff", "delay input", onoff, 2)
  params:set_action("delay_input_onoff",function(value)
      osc.send({"localhost",57120},"/receiver",{2,params:get("delay_input")*(value-1)})
      if value == 1 then
        delin = false
      else
        delin = true
      end
  end
  )
  params:add_option("delay_output_onoff", "delay output", onoff, 2)
  params:set_action("delay_output_onoff",function(value)
      osc.send({"localhost",57120},"/receiver",{3,value})
      if value == 1 then
        delout = false
      else
        delout = true
      end
  end
  )
  params:add_group("levels",2)
  params:add_control("input_passthrough","input passthru level",controlspec.DB)
  params:set_action("input_passthrough",function(value)
      params:set('monitor_level',value)
      remember = value
  end
  )
  params:set("input_passthrough",0)
  params:add_control("delay_input","delay input level",controlspec.AMP)
  params:set_action("delay_input",function(value)
      osc.send({"localhost",57120},"/receiver",{2,value})
  end
  )
  params:set("delay_input",0.5)
  params:bang()
end

--------------------------
-- redraw 
--------------------------
function redraw()
  screen.clear()
  screen.font_size(8)
  screen.font_face(1)
  screen.move(1,15)
  if inpass then
    screen.level(15)
  else
    screen.level(5)
  end
  screen.text("input passthru")
  screen.move(75,15)
  screen.text(math.floor(params:get("monitor_level")*100)/100)
  screen.move(36,35)
  if delin then
    screen.level(15)
  else
    screen.level(5)
  end
  screen.text("delay input")
  screen.move(93,35)
  screen.text(params:get("delay_input"))
  screen.move(61,55)
  if delout then
    screen.level(15)
  else
    screen.level(5)
  end
  screen.text("delay output")
  screen.update()
end

function cleanup ()
  params:set('monitor_level', pre_init_monitor_level) -- restore 'monitor' level
  poll.clear_all()
end