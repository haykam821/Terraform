package com.terraformersmc.terraform.block;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.*;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateFactory;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.IWorld;
import net.minecraft.world.ViewableWorld;
import net.minecraft.world.World;

import java.util.Random;
import java.util.function.Supplier;

// Rather complex: combine the function of leaves, logs, and cobblestone walls.

/**
 * A very complex smaller log block that can connect on all 6 axes, can be waterlogged, and can have leaves embedded.
 * Used for things like the Sakura tree.
 */
public class SmallLogBlock extends Block implements Waterloggable {
	public static final BooleanProperty HAS_LEAVES = BooleanProperty.of("has_leaves");
	public static final BooleanProperty UP = BooleanProperty.of("up");
	public static final BooleanProperty DOWN = BooleanProperty.of("down");
	public static final BooleanProperty NORTH = BooleanProperty.of("north");
	public static final BooleanProperty EAST = BooleanProperty.of("east");
	public static final BooleanProperty SOUTH = BooleanProperty.of("south");
	public static final BooleanProperty WEST = BooleanProperty.of("west");
	public static final BooleanProperty WATERLOGGED = BooleanProperty.of("waterlogged");

	private static final int UP_MASK = 1 << Direction.UP.ordinal();
	private static final int DOWN_MASK = 1 << Direction.DOWN.ordinal();
	private static final int NORTH_MASK = 1 << Direction.NORTH.ordinal();
	private static final int EAST_MASK = 1 << Direction.EAST.ordinal();
	private static final int SOUTH_MASK = 1 << Direction.SOUTH.ordinal();
	private static final int WEST_MASK = 1 << Direction.WEST.ordinal();

	protected final VoxelShape[] collisionShapes;
	protected final VoxelShape[] boundingShapes;
	private final Object2IntMap<BlockState> SHAPE_INDEX_CACHE = new Object2IntOpenHashMap<>();

	private final Block leaves;
	private final Supplier<Block> stripped;

	public SmallLogBlock(Block leaves, Supplier<Block> stripped, Block.Settings settings) {
		super(settings);
		this.setDefaultState(this.stateFactory.getDefaultState()
			.with(UP, false)
			.with(DOWN, false)
			.with(WEST, false)
			.with(EAST, false)
			.with(NORTH, false)
			.with(SOUTH, false)
			.with(WATERLOGGED, false)
			.with(HAS_LEAVES, false)
		);

		this.collisionShapes = this.createShapes(5);
		this.boundingShapes = this.createShapes(5);
		this.leaves = leaves;
		this.stripped = stripped;
	}

	private int getShapeIndex(BlockState requested) {
		return this.SHAPE_INDEX_CACHE.computeIntIfAbsent(requested, state -> {
			int mask = 0;

			if (state.get(UP)) {
				mask |= UP_MASK;
			}

			if (state.get(DOWN)) {
				mask |= DOWN_MASK;
			}

			if (state.get(NORTH)) {
				mask |= NORTH_MASK;
			}

			if (state.get(EAST)) {
				mask |= EAST_MASK;
			}

			if (state.get(SOUTH)) {
				mask |= SOUTH_MASK;
			}

			if (state.get(WEST)) {
				mask |= WEST_MASK;
			}

			return mask;
		});
	}

	public VoxelShape[] createShapes(double radius) {
		double lower = 8.0 - radius;
		double upper = 8.0 + radius;

		VoxelShape center = Block.createCuboidShape(lower, lower, lower, upper, upper, upper);

		VoxelShape down = Block.createCuboidShape(lower, 0.0, lower, upper, lower, upper);
		VoxelShape up = Block.createCuboidShape(lower, upper, lower, upper, 16.0, upper);

		// Minus Z: North
		VoxelShape north = Block.createCuboidShape(lower, lower, 0.0, upper, upper, lower);
		VoxelShape south = Block.createCuboidShape(lower, lower, upper, upper, upper, 16.0);

		// Minus X: West
		VoxelShape west = Block.createCuboidShape(0.0, lower, lower, lower, upper, upper);
		VoxelShape east = Block.createCuboidShape(upper, lower, lower, 16.0, upper, upper);

		VoxelShape[] shapes = new VoxelShape[64];

		for (int i = 0; i < 64; i++) {
			VoxelShape shape = center;

			if ((i & DOWN_MASK) != 0) {
				shape = VoxelShapes.union(shape, down);
			}

			if ((i & UP_MASK) != 0) {
				shape = VoxelShapes.union(shape, up);
			}

			if ((i & NORTH_MASK) != 0) {
				shape = VoxelShapes.union(shape, north);
			}

			if ((i & SOUTH_MASK) != 0) {
				shape = VoxelShapes.union(shape, south);
			}

			if ((i & WEST_MASK) != 0) {
				shape = VoxelShapes.union(shape, west);
			}

			if ((i & EAST_MASK) != 0) {
				shape = VoxelShapes.union(shape, east);
			}

			shapes[i] = shape;
		}

		return shapes;
	}

	@Environment(EnvType.CLIENT)
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (state.get(HAS_LEAVES)) {
			Blocks.OAK_LEAVES.randomDisplayTick(state, world, pos, random);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean activate(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult result) {
		ItemStack held = player.getStackInHand(hand);

		if (held.getCount() >= 1 && held.getItem() == Item.BLOCK_ITEMS.get(leaves) && !state.get(HAS_LEAVES)) {
			if (!player.isCreative()) {
				held.decrement(1);
			}

			BlockSoundGroup sounds = leaves.getDefaultState().getSoundGroup();
			world.playSound(player, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS, (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);

			BlockState previous = state;

			state = state.with(HAS_LEAVES, true);

			if (state.get(UP) && world.getBlockState(pos.up()).getBlock() instanceof LeavesBlock) {
				state = state.with(UP, false);
			}

			if (state.get(DOWN) && world.getBlockState(pos.down()).getBlock() instanceof LeavesBlock) {
				state = state.with(DOWN, false);
			}

			if (state.get(WEST) && world.getBlockState(pos.west()).getBlock() instanceof LeavesBlock) {
				state = state.with(WEST, false);
			}

			if (state.get(EAST) && world.getBlockState(pos.east()).getBlock() instanceof LeavesBlock) {
				state = state.with(EAST, false);
			}

			if (state.get(NORTH) && world.getBlockState(pos.north()).getBlock() instanceof LeavesBlock) {
				state = state.with(NORTH, false);
			}

			if (state.get(SOUTH) && world.getBlockState(pos.south()).getBlock() instanceof LeavesBlock) {
				state = state.with(SOUTH, false);
			}

			world.setBlockState(pos, pushEntitiesUpBeforeBlockChange(previous, state, world, pos));

			return true;
		} else if(stripped != null && held.getItem() instanceof MiningToolItem) {
			MiningToolItem tool = (MiningToolItem) held.getItem();

			if(tool.isEffectiveOn(state) || tool.getMiningSpeed(held, state) > 1.0F) {
				world.playSound(player, pos, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F);

				if(!world.isClient) {
					BlockState target = stripped.get().getDefaultState()
							.with(SmallLogBlock.UP, state.get(SmallLogBlock.UP))
							.with(SmallLogBlock.DOWN, state.get(SmallLogBlock.DOWN))
							.with(SmallLogBlock.NORTH, state.get(SmallLogBlock.NORTH))
							.with(SmallLogBlock.SOUTH, state.get(SmallLogBlock.SOUTH))
							.with(SmallLogBlock.EAST, state.get(SmallLogBlock.EAST))
							.with(SmallLogBlock.WEST, state.get(SmallLogBlock.WEST))
							.with(SmallLogBlock.WATERLOGGED, state.get(SmallLogBlock.WATERLOGGED))
							.with(SmallLogBlock.HAS_LEAVES, state.get(SmallLogBlock.HAS_LEAVES));

					world.setBlockState(pos, target);

					held.damage(1, player, consumedPlayer -> consumedPlayer.sendToolBreakStatus(hand));
				}

				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean isOpaque(BlockState state) {
		return false;
	}

	@Override
	public BlockRenderLayer getRenderLayer() {
		return Blocks.OAK_LEAVES.getRenderLayer();
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean canSuffocate(BlockState state, BlockView view, BlockPos pos) {
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean allowsSpawning(BlockState state, BlockView view, BlockPos pos, EntityType<?> entityType) {
		return Blocks.OAK_LEAVES.allowsSpawning(state, view, pos, entityType);
	}

	@Override
	protected void appendProperties(StateFactory.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);

		builder.add(HAS_LEAVES, UP, DOWN, NORTH, SOUTH, EAST, WEST, WATERLOGGED);
	}

	private boolean shouldConnectTo(BlockState state, boolean solid, boolean leaves) {
		Block block = state.getBlock();

		return solid || (!leaves && block instanceof LeavesBlock) || block instanceof SmallLogBlock;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
		super.onPlaced(world, pos, state, entity, stack);

		for (Direction direction : Direction.values()) {
			if (world.getBlockState(pos.offset(direction)).getBlock() instanceof SmallLogBlock) {
				world.setBlockState(pos.offset(direction),
					getNeighborUpdateState(world.getBlockState(pos.offset(direction)),
						direction.getOpposite(),
						world.getBlockState(pos),
						world,
						pos.offset(direction),
						pos));
			}
		}
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {

		if (context.getPlayer() == null) {
			return context.getWorld().getBlockState(context.getBlockPos()).getBlock().equals(Blocks.WATER) ? this.getDefaultState().with(WATERLOGGED, true) : this.getDefaultState();
		}

		ViewableWorld world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		FluidState fluid = context.getWorld().getFluidState(context.getBlockPos());

		BlockPos upPos = pos.up();
		BlockPos downPos = pos.down();
		BlockPos northPos = pos.north();
		BlockPos eastPos = pos.east();
		BlockPos southPos = pos.south();
		BlockPos westPos = pos.west();

		BlockState upState = world.getBlockState(upPos);
		BlockState downState = world.getBlockState(downPos);
		BlockState northState = world.getBlockState(northPos);
		BlockState eastState = world.getBlockState(eastPos);
		BlockState southState = world.getBlockState(southPos);
		BlockState westState = world.getBlockState(westPos);

		boolean up = this.shouldConnectTo(upState, Block.isSolidFullSquare(upState, world, upPos, Direction.UP), false);
		boolean down = this.shouldConnectTo(downState, Block.isSolidFullSquare(downState, world, downPos, Direction.DOWN), false);
		boolean north = this.shouldConnectTo(northState, Block.isSolidFullSquare(northState, world, northPos, Direction.SOUTH), false);
		boolean east = this.shouldConnectTo(eastState, Block.isSolidFullSquare(eastState, world, eastPos, Direction.WEST), false);
		boolean south = this.shouldConnectTo(southState, Block.isSolidFullSquare(southState, world, southPos, Direction.NORTH), false);
		boolean west = this.shouldConnectTo(westState, Block.isSolidFullSquare(westState, world, westPos, Direction.EAST), false);

		return this.getDefaultState()
			.with(UP, up)
			.with(DOWN, down)
			.with(NORTH, north)
			.with(EAST, east)
			.with(SOUTH, south)
			.with(WEST, west)
			.with(WATERLOGGED, fluid.getFluid() == Fluids.WATER);
	}

	@Override
	@SuppressWarnings("deprecation")
	public FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		switch (rotation) {
			case CLOCKWISE_180:
				return state.with(NORTH, state.get(SOUTH)).with(EAST, state.get(WEST)).with(SOUTH, state.get(NORTH)).with(WEST, state.get(EAST));
			case COUNTERCLOCKWISE_90:
				return state.with(NORTH, state.get(EAST)).with(EAST, state.get(SOUTH)).with(SOUTH, state.get(WEST)).with(WEST, state.get(NORTH));
			case CLOCKWISE_90:
				return state.with(NORTH, state.get(WEST)).with(EAST, state.get(NORTH)).with(SOUTH, state.get(EAST)).with(WEST, state.get(SOUTH));
			default:
				return state;
		}

	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		switch (mirror) {
			case LEFT_RIGHT:
				return state.with(NORTH, state.get(SOUTH)).with(SOUTH, state.get(NORTH));
			case FRONT_BACK:
				return state.with(EAST, state.get(WEST)).with(WEST, state.get(EAST));
			default:
				return super.mirror(state, mirror);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean canPlaceAtSide(BlockState state, BlockView view, BlockPos pos, BlockPlacementEnvironment blockPlacementEnvironment_1) {
		return false;
	}

	public BlockState getNeighborUpdateState(BlockState state, Direction fromDirection, BlockState neighbor, IWorld world, BlockPos pos, BlockPos neighborPos) {
		if (state.get(WATERLOGGED)) {
			world.getFluidTickScheduler().schedule(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}

		boolean leaves = state.get(HAS_LEAVES);

		boolean up = fromDirection == Direction.UP && this.shouldConnectTo(neighbor, Block.isSolidFullSquare(neighbor, world, neighborPos, Direction.DOWN), leaves) || state.get(UP);
		boolean down = fromDirection == Direction.DOWN && this.shouldConnectTo(neighbor, Block.isSolidFullSquare(neighbor, world, neighborPos, Direction.UP), leaves) || state.get(DOWN);
		boolean north = fromDirection == Direction.NORTH && this.shouldConnectTo(neighbor, Block.isSolidFullSquare(neighbor, world, neighborPos, Direction.SOUTH), leaves) || state.get(NORTH);
		boolean east = fromDirection == Direction.EAST && this.shouldConnectTo(neighbor, Block.isSolidFullSquare(neighbor, world, neighborPos, Direction.WEST), leaves) || state.get(EAST);
		boolean south = fromDirection == Direction.SOUTH && this.shouldConnectTo(neighbor, Block.isSolidFullSquare(neighbor, world, neighborPos, Direction.NORTH), leaves) || state.get(SOUTH);
		boolean west = fromDirection == Direction.WEST && this.shouldConnectTo(neighbor, Block.isSolidFullSquare(neighbor, world, neighborPos, Direction.EAST), leaves) || state.get(WEST);

		return state
			.with(UP, up)
			.with(DOWN, down)
			.with(NORTH, north)
			.with(EAST, east)
			.with(SOUTH, south)
			.with(WEST, west);
	}

	@Override
	public boolean isTranslucent(BlockState state, BlockView view, BlockPos pos) {
		return !state.get(WATERLOGGED);
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getOutlineShape(BlockState state, BlockView view, BlockPos pos, EntityContext context) {
		return state.get(HAS_LEAVES) ? VoxelShapes.fullCube() : this.boundingShapes[this.getShapeIndex(state)];
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getCollisionShape(BlockState state, BlockView view, BlockPos pos, EntityContext context) {
		return state.get(HAS_LEAVES) ? VoxelShapes.fullCube() : this.collisionShapes[this.getShapeIndex(state)];
	}
}
